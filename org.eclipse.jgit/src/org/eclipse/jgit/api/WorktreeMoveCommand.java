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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.worktree.Worktrees;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * Move a linked worktree to a new path.
 *
 * <p>
 * Calling {@link #call()} moves the working tree directory to the new
 * location and rewrites the {@code gitdir}/{@code .git} pointer pair so
 * both ends of the link remain consistent.
 *
 * <p>
 * The move is refused if the worktree is locked (unless
 * {@link #setForce(boolean)} is set) or if the destination already exists.
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeMoveCommand extends GitCommand<Void> {

	private String name;

	private File newPath;

	private boolean force;

	/**
	 * Constructor for WorktreeMoveCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeMoveCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the worktree name to move.
	 *
	 * @param name
	 *            admin-directory base name of the worktree
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeMoveCommand setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Set the new path for the worktree.
	 *
	 * @param newPath
	 *            destination directory; must not exist or must be empty
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeMoveCommand setNewPath(File newPath) {
		this.newPath = newPath;
		return this;
	}

	/**
	 * Allow moving a locked worktree.
	 *
	 * @param force
	 *            {@code true} to bypass the locked check
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeMoveCommand setForce(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return {@code null}
	 * @throws GitAPIException
	 *             if the worktree cannot be moved
	 */
	@Override
	public Void call() throws GitAPIException {
		checkCallable();
		try {
			doMove();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
		return null;
	}

	private void doMove() throws IOException {
		if (name == null || name.isEmpty()) {
			throw new JGitInternalException(JGitText.get().worktreePathRequired);
		}
		if (newPath == null) {
			throw new JGitInternalException(JGitText.get().worktreePathRequired);
		}
		File dest = newPath.getAbsoluteFile();

		File adminDir = WorktreeLockCommand.resolveAdminDir(repo, name);

		if (!force && Worktrees.isLocked(adminDir)) {
			String reason = Worktrees.readLockReason(adminDir);
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeIsLocked,
					name,
					reason != null ? reason : "")); //$NON-NLS-1$
		}

		File oldPath = Worktrees.readWorktreePath(adminDir);
		if (oldPath == null || !oldPath.isDirectory()) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeNotFound, name));
		}

		if (dest.exists()) {
			String[] children = dest.list();
			if (children != null && children.length > 0) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().worktreePathExists, dest));
			}
		}

		// Move the working tree
		FileUtils.rename(oldPath, dest);

		// Rewrite the pointer pair
		rewritePointerPair(adminDir, dest);
	}

	/**
	 * Rewrite the {@code gitdir} file inside the admin directory and the
	 * {@code .git} file inside the working tree so both point at each other
	 * with the updated paths.
	 *
	 * @param adminDir
	 *            the {@code .git/worktrees/<name>} directory
	 * @param wtPath
	 *            the (new) working tree directory
	 * @throws IOException
	 *             if writing fails
	 */
	static void rewritePointerPair(File adminDir, File wtPath)
			throws IOException {
		File dotGit = new File(wtPath, Constants.DOT_GIT);
		// adminDir/gitdir → absolute path to <wtPath>/.git
		Files.write(new File(adminDir, Constants.GITDIR_FILE).toPath(),
				(dotGit.getAbsolutePath() + "\n").getBytes(UTF_8)); //$NON-NLS-1$
		// <wtPath>/.git → "gitdir: <adminDir>\n"
		Files.write(dotGit.toPath(),
				("gitdir: " + adminDir.getAbsolutePath() + "\n").getBytes(UTF_8)); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
