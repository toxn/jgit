/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.worktree;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.worktree.WorktreeReference;

/**
 * Utilities for reading and writing worktree admin metadata from
 * {@code .git/worktrees/<name>/}.
 *
 * <p>
 * Callers should generally prefer using {@link WorktreeReference} and the
 * {@code Worktree*Command} classes in {@code org.eclipse.jgit.api} rather than
 * calling these methods directly.
 */
public class Worktrees {

	/**
	 * List all worktrees of the given repository: main worktree first, then
	 * linked worktrees in filesystem order.
	 *
	 * @param repo
	 *            the repository to inspect; must not be {@code null}
	 * @return unmodifiable list of {@link WorktreeReference} entries; never
	 *         {@code null}
	 * @throws IOException
	 *             if reading the worktrees directory fails
	 */
	public static List<WorktreeReference> list(Repository repo) throws IOException {
		List<WorktreeReference> result = new ArrayList<>();

		// --- Main worktree ---
		result.add(buildMain(repo));

		// --- Linked worktrees ---
		File worktreesDir = new File(repo.getCommonDirectory(),
				Constants.WORKTREES);
		if (worktreesDir.isDirectory()) {
			File[] entries = worktreesDir.listFiles();
			if (entries != null) {
				for (File adminDir : entries) {
					if (!adminDir.isDirectory()) {
						continue;
					}
					WorktreeReference ref = buildLinked(repo, adminDir);
					if (ref != null) {
						result.add(ref);
					}
				}
			}
		}

		return result;
	}

	/**
	 * Resolve the HEAD of the given linked-worktree admin directory without
	 * opening a full Repository object.
	 *
	 * <p>
	 * This reads the {@code HEAD} file inside {@code adminDir} (which acts as
	 * the per-worktree gitDir). A symbolic HEAD is followed one level: the
	 * branch name is extracted but the tip commit is not resolved here (the
	 * caller must use the common object database for that).
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @return a two-element array {@code [commitId, branchRef]} where either
	 *         element may be {@code null}; {@code branchRef} is the full ref
	 *         name for a symbolic HEAD, or {@code null} for a detached HEAD.
	 * @throws IOException
	 *             if reading the {@code HEAD} file fails
	 */
	public static String[] readHead(File adminDir) throws IOException {
		File headFile = new File(adminDir, Constants.HEAD);
		if (!headFile.isFile()) {
			return new String[] { null, null };
		}
		String content = readFileUtf8(headFile).trim();
		if (content.startsWith(Constants.R_REFS)) {
			// packed symbolic ref written directly as ref name
			return new String[] { null, content };
		}
		if (content.startsWith("ref: ")) { //$NON-NLS-1$
			return new String[] { null, content.substring("ref: ".length()).trim() }; //$NON-NLS-1$
		}
		// detached — raw SHA1
		if (ObjectId.isId(content)) {
			return new String[] { content, null };
		}
		return new String[] { null, null };
	}

	/**
	 * Read the absolute working-tree path for a linked worktree from the
	 * {@code gitdir} file written by {@code git worktree add} inside the
	 * working tree's {@code .git} pointer file.
	 *
	 * <p>
	 * The {@code gitdir} file inside the admin directory contains the absolute
	 * path back to the {@code .git} file in the working tree. From that we
	 * derive the working tree path (its parent directory).
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @return the working tree directory, or {@code null} if the path cannot
	 *         be resolved
	 * @throws IOException
	 *             if reading the {@code gitdir} file fails
	 */
	public static File readWorktreePath(File adminDir) throws IOException {
		File gitdirFile = new File(adminDir, Constants.GITDIR_FILE);
		if (!gitdirFile.isFile()) {
			return null;
		}
		String path = readFileUtf8(gitdirFile).trim();
		File dotGitFile = new File(path);
		if (!dotGitFile.isAbsolute()) {
			// Spec says absolute, but be defensive
			dotGitFile = new File(adminDir, path);
		}
		// The .git pointer file lives inside the working tree
		return dotGitFile.getParentFile();
	}

	/**
	 * Whether the worktree admin directory is marked locked.
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @return {@code true} if the {@code locked} file is present
	 */
	public static boolean isLocked(File adminDir) {
		return new File(adminDir, Constants.LOCKED).isFile();
	}

	/**
	 * Read the lock reason from the admin directory.
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @return the trimmed content of the {@code locked} file, or {@code null}
	 *         if not locked or the file is empty
	 * @throws IOException
	 *             if reading the file fails
	 */
	public static String readLockReason(File adminDir) throws IOException {
		File lockFile = new File(adminDir, Constants.LOCKED);
		if (!lockFile.isFile()) {
			return null;
		}
		String reason = readFileUtf8(lockFile).trim();
		return reason.isEmpty() ? null : reason;
	}

	/**
	 * Whether a linked worktree is prunable.
	 *
	 * <p>
	 * A worktree is prunable if the working tree path it points to no longer
	 * exists, or if the {@code gitdir} back-link inside the admin directory is
	 * stale (the pointed-to file does not exist).
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @return {@code true} if the worktree should be pruned
	 * @throws IOException
	 *             if reading files fails
	 */
	public static boolean isPrunable(File adminDir) throws IOException {
		File gitdirFile = new File(adminDir, Constants.GITDIR_FILE);
		if (!gitdirFile.isFile()) {
			return true;
		}
		String path = readFileUtf8(gitdirFile).trim();
		File dotGitFile = new File(path);
		if (!dotGitFile.isAbsolute()) {
			dotGitFile = new File(adminDir, path);
		}
		return !dotGitFile.exists();
	}

	/**
	 * Write the {@code locked} file to the admin directory.
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @param reason
	 *            human-readable reason; may be {@code null} or empty
	 * @throws IOException
	 *             if writing fails
	 */
	public static void writeLocked(File adminDir, String reason) throws IOException {
		File lockFile = new File(adminDir, Constants.LOCKED);
		String content = (reason != null) ? reason : ""; //$NON-NLS-1$
		Files.write(lockFile.toPath(), content.getBytes(UTF_8));
	}

	/**
	 * Delete the {@code locked} file from the admin directory.
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @return {@code true} if the file existed and was deleted
	 */
	public static boolean deleteLocked(File adminDir) {
		return new File(adminDir, Constants.LOCKED).delete();
	}

	/**
	 * Check whether the given branch is already checked out in any linked
	 * worktree other than {@code excludeName}.
	 *
	 * @param repo
	 *            repository whose common directory to scan
	 * @param branchRef
	 *            full ref name to check (e.g. {@code refs/heads/main})
	 * @param excludeName
	 *            admin-dir name to skip (the worktree being added); may be
	 *            {@code null}
	 * @return {@code true} if the branch is checked out in another worktree
	 * @throws IOException
	 *             if reading admin dirs fails
	 */
	public static boolean isBranchCheckedOut(Repository repo, String branchRef,
			String excludeName) throws IOException {
		if (branchRef == null) {
			return false;
		}
		// Check main worktree HEAD
		Ref mainHead = repo.exactRef(Constants.HEAD);
		if (mainHead != null && branchRef.equals(mainHead.getTarget().getName())) {
			return true;
		}
		// Check linked worktrees
		File worktreesDir = new File(repo.getCommonDirectory(),
				Constants.WORKTREES);
		if (!worktreesDir.isDirectory()) {
			return false;
		}
		File[] entries = worktreesDir.listFiles();
		if (entries == null) {
			return false;
		}
		for (File adminDir : entries) {
			if (!adminDir.isDirectory()) {
				continue;
			}
			if (adminDir.getName().equals(excludeName)) {
				continue;
			}
			String[] headInfo = readHead(adminDir);
			if (branchRef.equals(headInfo[1])) {
				return true;
			}
		}
		return false;
	}

	// --- private helpers ---

	private static WorktreeReference buildMain(Repository repo)
			throws IOException {
		WorktreeReference.Builder b = new WorktreeReference.Builder();
		b.setName(""); //$NON-NLS-1$
		b.setGitDir(repo.getDirectory());
		b.setBare(repo.isBare());

		if (!repo.isBare()) {
			// Repository.getWorkTree() throws NoWorkTreeException for a bare
			// repository, so it must only be called once we know there is one.
			b.setWorktreePath(repo.getWorkTree());
			Ref headRef = repo.exactRef(Constants.HEAD);
			if (headRef != null) {
				if (headRef.isSymbolic()) {
					b.setBranch(headRef.getTarget().getName());
				} else {
					b.setDetached(true);
				}
				AnyObjectId headObj = headRef.getObjectId();
				if (headObj != null) {
					b.setHead(headObj.toObjectId());
				}
			}
		}
		return b.build();
	}

	private static WorktreeReference buildLinked(Repository repo, File adminDir)
			throws IOException {
		WorktreeReference.Builder b = new WorktreeReference.Builder();
		b.setName(adminDir.getName());
		b.setGitDir(adminDir);

		// Working tree path
		File worktreePath = readWorktreePath(adminDir);
		b.setWorktreePath(worktreePath);

		// HEAD (branch or detached); resolve the tip commit via the common
		// object database so the SHA is always populated.
		String[] headInfo = readHead(adminDir);
		if (headInfo[0] != null) {
			b.setHead(ObjectId.fromString(headInfo[0]));
			b.setDetached(true);
		} else if (headInfo[1] != null) {
			b.setBranch(headInfo[1]);
			Ref branchRef = repo.exactRef(headInfo[1]);
			if (branchRef != null && branchRef.getObjectId() != null) {
				b.setHead(branchRef.getObjectId());
			}
		}

		// Locked
		boolean locked = isLocked(adminDir);
		b.setLocked(locked);
		if (locked) {
			b.setLockReason(readLockReason(adminDir));
		}

		// Prunable (only for unlocked worktrees)
		if (!locked) {
			boolean prunable = isPrunable(adminDir);
			b.setPrunable(prunable);
			if (prunable) {
				b.setPrunableReason(buildPrunableReason(adminDir));
			}
		}

		return b.build();
	}

	private static String buildPrunableReason(File adminDir) throws IOException {
		File gitdirFile = new File(adminDir, Constants.GITDIR_FILE);
		if (!gitdirFile.isFile()) {
			return "gitdir file missing"; //$NON-NLS-1$
		}
		String path = readFileUtf8(gitdirFile).trim();
		File dotGitFile = new File(path);
		if (!dotGitFile.isAbsolute()) {
			dotGitFile = new File(adminDir, path);
		}
		if (!dotGitFile.exists()) {
			return "gitdir file points at non-existent location"; //$NON-NLS-1$
		}
		return null;
	}

	private static String readFileUtf8(File file) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());
		return new String(bytes, UTF_8);
	}

	private Worktrees() {
		// utility class
	}
}
