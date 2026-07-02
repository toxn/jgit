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

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.internal.worktree.Worktrees;
import org.eclipse.jgit.worktree.WorktreeReference;

/**
 * List all worktrees of a repository.
 *
 * <p>
 * Calling {@link #call()} returns an ordered list of
 * {@link org.eclipse.jgit.worktree.WorktreeReference} objects. The main
 * worktree is always the first element; linked worktrees follow in filesystem
 * order.
 *
 * <p>
 * Example:
 *
 * <pre>
 * try (Git git = Git.open(repoDir)) {
 *     List&lt;WorktreeReference&gt; worktrees = git.worktreeList().call();
 *     for (WorktreeReference wt : worktrees) {
 *         System.out.println(wt.getName() + " " + wt.getWorktreePath());
 *     }
 * }
 * </pre>
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeListCommand
		extends GitCommand<List<WorktreeReference>> {

	/**
	 * Constructor for WorktreeListCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeListCommand(Repository repo) {
		super(repo);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Execute the {@code worktree list} command.
	 *
	 * @return an unmodifiable ordered list of all worktrees: main worktree
	 *         first, then linked worktrees. Never {@code null}.
	 * @throws GitAPIException
	 *             if listing the worktrees fails
	 */
	@Override
	public List<WorktreeReference> call() throws GitAPIException {
		checkCallable();
		try {
			return Worktrees.list(repo);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
	}
}
