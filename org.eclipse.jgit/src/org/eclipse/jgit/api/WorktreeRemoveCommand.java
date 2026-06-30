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
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FileUtils;

/**
 * Remove a linked worktree.
 *
 * <p>
 * Calling {@link #call()} removes the given linked worktree. The worktree must
 * be identified by name (the base name of its admin directory under
 * {@code .git/worktrees/<name>}).
 *
 * <p>
 * The remove is refused if:
 * <ul>
 * <li>the worktree is locked (unless {@link #setForce(boolean)} is set)</li>
 * <li>the worktree has uncommitted changes (unless {@link #setForce(boolean)}
 * is set)</li>
 * </ul>
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeRemoveCommand extends GitCommand<Void> {

	private String name;

	private boolean force;

	/**
	 * Constructor for WorktreeRemoveCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeRemoveCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the name of the worktree to remove (the admin-directory base name,
	 * i.e. the last component of the path passed to {@code worktree add}).
	 *
	 * @param name
	 *            worktree name; must not be {@code null} or empty
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeRemoveCommand setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Allow removing a locked or dirty worktree.
	 *
	 * @param force
	 *            {@code true} to bypass locked and dirty checks
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeRemoveCommand setForce(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Execute the {@code worktree remove} command.
	 *
	 * @return {@code null}
	 * @throws GitAPIException
	 *             if the worktree cannot be removed
	 */
	@Override
	public Void call() throws GitAPIException {
		checkCallable();
		try {
			doRemove();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
		return null;
	}

	private void doRemove() throws IOException, GitAPIException {
		if (name == null || name.isEmpty()) {
			throw new JGitInternalException(JGitText.get().worktreePathRequired);
		}

		File commonDir = repo.getCommonDirectory();
		File adminDir = new File(new File(commonDir, Constants.WORKTREES), name);
		if (!adminDir.isDirectory()) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeNotFound, name));
		}

		// --- Locked check ---
		if (!force && Worktrees.isLocked(adminDir)) {
			String reason = Worktrees.readLockReason(adminDir);
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeIsLocked,
					name,
					reason != null ? reason : "")); //$NON-NLS-1$
		}

		// --- Dirty check (uncommitted changes) ---
		File wtPath = Worktrees.readWorktreePath(adminDir);
		if (!force && wtPath != null && wtPath.isDirectory()) {
			try (Repository worktreeRepo = new FileRepositoryBuilder()
					.setGitDir(adminDir)
					.setGitCommonDir(commonDir)
					.setWorkTree(wtPath)
					.build()) {
				if (hasUncommittedChanges(worktreeRepo)) {
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().worktreeHasUncommittedChanges, name));
				}
			}
		}

		// --- Delete working tree ---
		if (wtPath != null && wtPath.isDirectory()) {
			FileUtils.delete(wtPath, FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
		}

		// --- Delete admin dir ---
		FileUtils.delete(adminDir, FileUtils.RECURSIVE);
	}

	private static boolean hasUncommittedChanges(Repository worktreeRepo)
			throws IOException {
		IndexDiff diff = new IndexDiff(worktreeRepo, Constants.HEAD,
				new FileTreeIterator(worktreeRepo));
		diff.diff();
		return !diff.getModified().isEmpty()
				|| !diff.getAdded().isEmpty()
				|| !diff.getRemoved().isEmpty()
				|| !diff.getMissing().isEmpty()
				|| !diff.getConflicting().isEmpty()
				|| !diff.getUntracked().isEmpty();
	}
}
