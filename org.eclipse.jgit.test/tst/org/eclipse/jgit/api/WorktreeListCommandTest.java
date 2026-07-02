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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.worktree.WorktreeReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link WorktreeListCommand} that do not require the native
 * {@code git} binary. Worktree admin metadata is written manually to the
 * repository's {@code .git/worktrees/<name>/} directory.
 */
public class WorktreeListCommandTest extends RepositoryTestCase {

	private ObjectId firstCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content"); //$NON-NLS-1$
			git.add().addFilepattern("file.txt").call(); //$NON-NLS-1$
			firstCommit = git.commit().setMessage("Initial commit").call() //$NON-NLS-1$
					.getId();
		}
	}

	@Test
	public void testListMainOnly() throws Exception {
		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(1, wts.size());
		WorktreeReference main = wts.get(0);
		assertTrue(main.isMain());
		assertEquals("", main.getName()); //$NON-NLS-1$
		assertEquals(db.getDirectory(), main.getGitDir());
		assertEquals(db.getWorkTree(), main.getWorktreePath());
		assertNotNull(main.getHead());
		assertEquals(firstCommit, main.getHead());
		assertEquals("refs/heads/master", main.getBranch()); //$NON-NLS-1$
		assertFalse(main.isDetached());
		assertFalse(main.isBare());
		assertFalse(main.isLocked());
		assertFalse(main.isPrunable());
	}

	@Test
	public void testListWithLinkedWorktreeOnBranch() throws Exception {
		File adminDir = createLinkedAdminDir("wt1", //$NON-NLS-1$
				"ref: refs/heads/master\n"); //$NON-NLS-1$
		createDotGitPointer(adminDir, "wt1"); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(2, wts.size());
		WorktreeReference linked = wts.get(1);
		assertEquals("wt1", linked.getName()); //$NON-NLS-1$
		assertFalse(linked.isMain());
		assertEquals("refs/heads/master", linked.getBranch()); //$NON-NLS-1$
		// SHA must be resolved via the common ref-db even for a linked worktree
		assertNotNull("HEAD SHA must be resolved for a linked worktree on a branch", //$NON-NLS-1$
				linked.getHead());
		assertEquals(firstCommit, linked.getHead());
		assertFalse(linked.isDetached());
		assertFalse(linked.isLocked());
	}

	@Test
	public void testListWithDetachedHead() throws Exception {
		String sha = firstCommit.getName();
		File adminDir = createLinkedAdminDir("wt-detached", sha + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
		createDotGitPointer(adminDir, "wt-detached"); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(2, wts.size());
		WorktreeReference linked = wts.get(1);
		assertEquals("wt-detached", linked.getName()); //$NON-NLS-1$
		assertTrue(linked.isDetached());
		assertNull(linked.getBranch());
		assertNotNull(linked.getHead());
		assertEquals(firstCommit, linked.getHead());
	}

	@Test
	public void testListWithLockedWorktree() throws Exception {
		File adminDir = createLinkedAdminDir("wt-locked", //$NON-NLS-1$
				"ref: refs/heads/master\n"); //$NON-NLS-1$
		createDotGitPointer(adminDir, "wt-locked"); //$NON-NLS-1$
		// Create the locked file with a reason
		write(new File(adminDir, "locked"), "held by CI"); //$NON-NLS-1$ //$NON-NLS-2$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(2, wts.size());
		WorktreeReference linked = wts.get(1);
		assertTrue(linked.isLocked());
		assertEquals("held by CI", linked.getLockReason()); //$NON-NLS-1$
	}

	@Test
	public void testListWithLockedNoReason() throws Exception {
		File adminDir = createLinkedAdminDir("wt-locked-noreason", //$NON-NLS-1$
				"ref: refs/heads/master\n"); //$NON-NLS-1$
		createDotGitPointer(adminDir, "wt-locked-noreason"); //$NON-NLS-1$
		// Empty locked file
		new File(adminDir, "locked").createNewFile(); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(2, wts.size());
		WorktreeReference linked = wts.get(1);
		assertTrue(linked.isLocked());
		assertNull(linked.getLockReason());
	}

	@Test
	public void testListWithPrunableWorktree() throws Exception {
		File adminDir = createLinkedAdminDir("wt-prunable", //$NON-NLS-1$
				"ref: refs/heads/master\n"); //$NON-NLS-1$
		// Write a gitdir pointing to a non-existent path
		write(new File(adminDir, "gitdir"), //$NON-NLS-1$
				"/nonexistent/path/.git\n"); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		assertEquals(2, wts.size());
		WorktreeReference linked = wts.get(1);
		assertFalse(linked.isLocked());
		assertTrue(linked.isPrunable());
		assertNotNull(linked.getPrunableReason());
	}

	@Test
	public void testListMultipleLinkedWorktrees() throws Exception {
		File admin1 = createLinkedAdminDir("alpha", //$NON-NLS-1$
				"ref: refs/heads/master\n"); //$NON-NLS-1$
		createDotGitPointer(admin1, "alpha"); //$NON-NLS-1$
		File admin2 = createLinkedAdminDir("beta", //$NON-NLS-1$
				firstCommit.getName() + "\n"); //$NON-NLS-1$
		createDotGitPointer(admin2, "beta"); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();

		// Main + 2 linked
		assertEquals(3, wts.size());
		assertTrue(wts.get(0).isMain());
	}

	// --- helpers ---

	/**
	 * Create a {@code .git/worktrees/<name>/} admin directory with the given
	 * {@code HEAD} content. Does NOT write a {@code gitdir} file.
	 */
	private File createLinkedAdminDir(String name, String headContent)
			throws IOException {
		File worktreesDir = new File(db.getDirectory(), "worktrees"); //$NON-NLS-1$
		File adminDir = new File(worktreesDir, name);
		adminDir.mkdirs();
		write(new File(adminDir, "HEAD"), headContent); //$NON-NLS-1$
		return adminDir;
	}

	/**
	 * Create a {@code .git} pointer file in a sibling directory of
	 * {@code db.getWorkTree()} and wire the {@code gitdir} file in the admin
	 * directory to point at it.
	 */
	private void createDotGitPointer(File adminDir, String worktreeName)
			throws IOException {
		File worktreeRoot = new File(db.getWorkTree().getParentFile(),
				worktreeName);
		worktreeRoot.mkdirs();
		File dotGitFile = new File(worktreeRoot, ".git"); //$NON-NLS-1$
		write(dotGitFile,
				"gitdir: " + adminDir.getAbsolutePath() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
		write(new File(adminDir, "gitdir"), //$NON-NLS-1$
				dotGitFile.getAbsolutePath() + "\n"); //$NON-NLS-1$
	}

}
