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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.worktree.Worktrees;
import org.eclipse.jgit.lib.Repository;

/**
 * Unlock a linked worktree.
 *
 * <p>
 * Calling {@link #call()} removes the {@code locked} file from the worktree's
 * admin directory, allowing the worktree to be pruned or removed.
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeUnlockCommand extends GitCommand<Void> {

	private String name;

	/**
	 * Constructor for WorktreeUnlockCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeUnlockCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the worktree name to unlock.
	 *
	 * @param name
	 *            admin-directory base name of the worktree
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeUnlockCommand setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return {@code null}
	 * @throws GitAPIException
	 *             if the worktree cannot be unlocked
	 */
	@Override
	public Void call() throws GitAPIException {
		checkCallable();
		try {
			doUnlock();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
		return null;
	}

	private void doUnlock() throws IOException {
		if (name == null || name.isEmpty()) {
			throw new JGitInternalException(JGitText.get().worktreePathRequired);
		}
		File adminDir = WorktreeLockCommand.resolveAdminDir(repo, name);

		if (!Worktrees.isLocked(adminDir)) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeNotLocked, name));
		}
		Worktrees.deleteLocked(adminDir);
	}
}
