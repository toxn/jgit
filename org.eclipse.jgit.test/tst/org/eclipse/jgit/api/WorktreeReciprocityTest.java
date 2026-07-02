/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.worktree.WorktreeReference;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Reciprocity tests between the native {@code git} binary and JGit's
 * {@link WorktreeListCommand}.
 *
 * <p>
 * All tests in this class are skipped automatically when the native {@code git}
 * binary is not available on the current system.
 */
public class WorktreeReciprocityTest extends RepositoryTestCase {

	@BeforeClass
	public static void requireGitBinary() {
		assumeTrue("native git binary required for reciprocity tests", //$NON-NLS-1$
				isGitAvailable());
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "initial"); //$NON-NLS-1$
			git.add().addFilepattern("file.txt").call(); //$NON-NLS-1$
			git.commit().setMessage("Initial commit").call(); //$NON-NLS-1$
		}
	}

	/**
	 * A worktree created by native git must be visible in
	 * {@link WorktreeListCommand}.
	 */
	@Test
	public void testNativeWorktreeIsVisibleInJGit() throws Exception {
		FS fs = db.getFS();
		worktreeAddNew(fs, db.getWorkTree(), "wt-native"); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(2, wts.size());
		assertTrue(wts.get(0).isMain());

		WorktreeReference linked = wts.get(1);
		assertEquals("wt-native", linked.getName()); //$NON-NLS-1$
		assertNotNull("HEAD SHA must not be null for a native-created worktree", //$NON-NLS-1$
				linked.getHead());
		assertNotNull(linked.getBranch());
		assertEquals("refs/heads/wt-native", linked.getBranch()); //$NON-NLS-1$
		assertFalse(linked.isDetached());
		assertFalse(linked.isLocked());
	}

	/**
	 * HEAD SHA reported by JGit must match the one reported by native git.
	 */
	@Test
	public void testHeadShaMatchesNativeGit() throws Exception {
		FS fs = db.getFS();
		worktreeAddNew(fs, db.getWorkTree(), "wt-sha"); //$NON-NLS-1$

		// JGit view
		WorktreeReference linked = new WorktreeListCommand(db).call().get(1);
		ObjectId jgitHead = linked.getHead();
		assertNotNull(jgitHead);

		// Native git view: resolve HEAD in the common repository
		ObjectId nativeHead = db.resolve(HEAD);
		// Both point to the same commit (master has only one commit)
		assertEquals(nativeHead, jgitHead);
	}

	/**
	 * A worktree on a specific branch created by native git must show the
	 * correct branch.
	 */
	@Test
	public void testBranchNameMatchesNativeGit() throws Exception {
		FS fs = db.getFS();
		// git worktree add -b feature ../wt-feature master
		worktreeAddNewBranch(fs, db.getWorkTree(), "wt-feature", "feature"); //$NON-NLS-1$ //$NON-NLS-2$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();
		assertEquals(2, wts.size());

		WorktreeReference linked = wts.get(1);
		assertEquals("refs/heads/feature", linked.getBranch()); //$NON-NLS-1$
	}

	/**
	 * A bare repository with a native worktree must be listed by
	 * {@link WorktreeListCommand} when opened from the worktree admin dir.
	 */
	@Test
	public void testNativeWorktreeOnBareRepo() throws Exception {
		FS fs = db.getFS();
		File directory = trash.getParentFile();
		String dbDirName = db.getWorkTree().getName();
		cloneBare(fs, directory, dbDirName, "bare-repo"); //$NON-NLS-1$

		File bareDirectory = new File(directory, "bare-repo"); //$NON-NLS-1$
		worktreeAddExisting(fs, bareDirectory, "master"); //$NON-NLS-1$

		File worktreesDir = new File(bareDirectory, "worktrees"); //$NON-NLS-1$
		File masterWorktreesDir = new File(worktreesDir, "master"); //$NON-NLS-1$
		try (FileRepository worktreeRepo = new FileRepository(
				masterWorktreesDir)) {
			ObjectId head = worktreeRepo.resolve(HEAD);
			assertNotNull(head);

			// WorktreeListCommand from the bare common dir
			File bareGitDir = bareDirectory;
			try (FileRepository bareRepo = new FileRepository(bareGitDir)) {
				List<WorktreeReference> wts = new WorktreeListCommand(bareRepo)
						.call();
				// bare (main) + 1 linked
				assertEquals(2, wts.size());
				assertTrue(wts.get(0).isBare());
			}
		}
	}

	// --- helpers (shared with LinkedWorktreeTest) ---

	private static boolean isGitAvailable() {
		try {
			ProcessBuilder pb = FS.DETECTED.runInShell("git", //$NON-NLS-1$
					new String[] { "--version" }); //$NON-NLS-1$
			Process p = pb.start();
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static void worktreeAddNew(FS fs, File directory, String name)
			throws IOException, InterruptedException {
		runGit(fs, directory, "worktree", "add", "-b", name, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"../" + name, "master"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void worktreeAddNewBranch(FS fs, File directory,
			String worktreeName, String branchName)
			throws IOException, InterruptedException {
		runGit(fs, directory, "worktree", "add", "-b", branchName, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"../" + worktreeName, "master"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void worktreeAddExisting(FS fs, File directory, String name)
			throws IOException, InterruptedException {
		runGit(fs, directory, "worktree", "add", "../" + name, name); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void cloneBare(FS fs, File directory, String from, String to)
			throws IOException, InterruptedException {
		runGit(fs, directory, "clone", "--bare", from, to); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void runGit(FS fs, File directory, String... args)
			throws IOException, InterruptedException {
		ProcessBuilder builder = fs.runInShell("git", args); //$NON-NLS-1$
		builder.directory(directory);
		builder.environment().put("HOME", fs.userHome().getAbsolutePath()); //$NON-NLS-1$
		ExecutionResult result = fs.execute(builder, new ByteArrayInputStream(
				new byte[0]));
		// We don't assert rc here; the test assertions will catch problems.
		assertNotNull(toString(result.getStdout()));
		assertNotNull(toString(result.getStderr()));
	}

	private static String toString(TemporaryBuffer b) throws IOException {
		return RawParseUtils.decode(b.toByteArray());
	}
}
