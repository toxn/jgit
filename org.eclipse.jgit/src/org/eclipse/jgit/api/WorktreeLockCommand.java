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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Lock a linked worktree to prevent it from being pruned.
 *
 * <p>
 * Calling {@link #call()} writes a {@code locked} file to the worktree's admin
 * directory. Pruning and automatic removal skip locked worktrees.
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeLockCommand extends GitCommand<Void> {

	private String name;

	private String reason;

	/**
	 * Constructor for WorktreeLockCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeLockCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the worktree name to lock.
	 *
	 * @param name
	 *            admin-directory base name of the worktree
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeLockCommand setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Set an optional human-readable lock reason.
	 *
	 * @param reason
	 *            lock reason; may be {@code null} or empty
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeLockCommand setReason(String reason) {
		this.reason = reason;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return {@code null}
	 * @throws GitAPIException
	 *             if the worktree cannot be locked
	 */
	@Override
	public Void call() throws GitAPIException {
		checkCallable();
		try {
			doLock();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
		return null;
	}

	private void doLock() throws IOException {
		if (name == null || name.isEmpty()) {
			throw new JGitInternalException(JGitText.get().worktreePathRequired);
		}
		File adminDir = resolveAdminDir(repo, name);

		if (Worktrees.isLocked(adminDir)) {
			String existing = Worktrees.readLockReason(adminDir);
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeAlreadyLocked,
					name,
					existing != null ? existing : "")); //$NON-NLS-1$
		}
		Worktrees.writeLocked(adminDir, reason);
	}

	static File resolveAdminDir(Repository repo, String name)
			throws JGitInternalException {
		File adminDir = new File(
				new File(repo.getCommonDirectory(), Constants.WORKTREES), name);
		if (!adminDir.isDirectory()) {
			throw new JGitInternalException(
					MessageFormat.format(JGitText.get().worktreeNotFound, name));
		}
		return adminDir;
	}
}
