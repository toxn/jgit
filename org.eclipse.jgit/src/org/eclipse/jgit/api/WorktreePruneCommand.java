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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.worktree.Worktrees;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * Prune stale linked worktree admin directories.
 *
 * <p>
 * Calling {@link #call()} scans {@code .git/worktrees/} and removes admin
 * directories whose working tree no longer exists (the {@code gitdir}
 * back-pointer points to a path that does not exist). Locked worktrees are
 * always skipped.
 *
 * <p>
 * Example:
 *
 * <pre>
 * try (Git git = Git.open(repoDir)) {
 *     List&lt;String&gt; pruned = git.worktreePrune().call();
 *     pruned.forEach(n -&gt; System.out.println("Removed stale worktree: " + n));
 * }
 * </pre>
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreePruneCommand extends GitCommand<List<String>> {

	private boolean dryRun;

	/**
	 * Constructor for WorktreePruneCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreePruneCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Report what would be pruned but do not actually remove anything.
	 *
	 * @param dryRun
	 *            {@code true} to skip the actual deletion
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreePruneCommand setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Execute the {@code worktree prune} command.
	 *
	 * @return names of the worktree admin directories that were (or would be,
	 *         with {@code --dry-run}) pruned; never {@code null}
	 * @throws GitAPIException
	 *             if scanning or deletion fails
	 */
	@Override
	public List<String> call() throws GitAPIException {
		checkCallable();
		try {
			return doPrune();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
	}

	private List<String> doPrune() throws IOException {
		File worktreesDir = new File(repo.getCommonDirectory(),
				Constants.WORKTREES);
		if (!worktreesDir.isDirectory()) {
			return Collections.emptyList();
		}

		File[] entries = worktreesDir.listFiles();
		if (entries == null) {
			return Collections.emptyList();
		}

		List<String> pruned = new ArrayList<>();
		for (File adminDir : entries) {
			if (!adminDir.isDirectory()) {
				continue;
			}
			// Locked worktrees are never pruned
			if (Worktrees.isLocked(adminDir)) {
				continue;
			}
			if (Worktrees.isPrunable(adminDir)) {
				pruned.add(adminDir.getName());
				if (!dryRun) {
					FileUtils.delete(adminDir, FileUtils.RECURSIVE);
				}
			}
		}
		return pruned;
	}
}
