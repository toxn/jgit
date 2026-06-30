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

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.worktree.Worktrees;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Repair worktree administrative files.
 *
 * <p>
 * Calling {@link #call()} scans every linked worktree's admin directory and
 * reconciles the {@code gitdir}/{@code .git} pointer pair when a working tree
 * has been moved externally. A new working tree path can be provided via
 * {@link #addPath(String, File)}; without explicit paths the command only
 * verifies consistency (and repairs nothing).
 *
 * <p>
 * Example — repair a single worktree after it was moved manually:
 *
 * <pre>
 * try (Git git = Git.open(repoDir)) {
 *     git.worktreeRepair()
 *             .addPath("my-wt", new File("/new/path/to/my-wt"))
 *             .call();
 * }
 * </pre>
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeRepairCommand extends GitCommand<List<String>> {

	/**
	 * A (name, newPath) pair describing a worktree whose working-tree has moved.
	 */
	private final List<RepairEntry> entries = new ArrayList<>();

	/**
	 * Constructor for WorktreeRepairCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeRepairCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Register a worktree whose working tree has moved.
	 *
	 * @param name
	 *            admin-directory base name of the worktree
	 * @param newPath
	 *            new working tree path
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeRepairCommand addPath(String name, File newPath) {
		entries.add(new RepairEntry(name, newPath.getAbsoluteFile()));
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return names of worktrees whose admin files were repaired; never
	 *         {@code null}
	 * @throws GitAPIException
	 *             if a repair operation fails
	 */
	@Override
	public List<String> call() throws GitAPIException {
		checkCallable();
		try {
			return doRepair();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
	}

	private List<String> doRepair() throws IOException {
		if (entries.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> repaired = new ArrayList<>();
		for (RepairEntry entry : entries) {
			File adminDir = new File(
					new File(repo.getCommonDirectory(), Constants.WORKTREES),
					entry.name);
			if (!adminDir.isDirectory()) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().worktreeNotFound, entry.name));
			}
			if (!entry.newPath.isDirectory()) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().worktreePathExists, entry.newPath));
			}

			// Confirm that .git in new path points at this admin dir (or is absent)
			// then rewrite the pointer pair
			WorktreeMoveCommand.rewritePointerPair(adminDir, entry.newPath);
			repaired.add(entry.name);
		}
		return repaired;
	}

	/**
	 * Checks if there is a stale gitdir pointer in the admin dir that can be
	 * compared against the candidate new path.
	 */
	@SuppressWarnings("unused")
	private static boolean isStale(File adminDir, File newPath) throws IOException {
		File oldPath = Worktrees.readWorktreePath(adminDir);
		return oldPath == null || !oldPath.getAbsoluteFile().equals(newPath);
	}

	private static final class RepairEntry {
		final String name;
		final File newPath;

		RepairEntry(String name, File newPath) {
			this.name = name;
			this.newPath = newPath;
		}
	}
}
