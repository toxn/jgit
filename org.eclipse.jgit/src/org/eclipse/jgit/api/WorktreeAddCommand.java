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
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.worktree.Worktrees;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Add a new linked worktree.
 *
 * <p>
 * Calling {@link #call()} creates a linked worktree at the specified path and
 * returns the opened {@link Repository} for that worktree. The caller is
 * responsible for closing the returned repository.
 *
 * <p>
 * Example:
 *
 * <pre>
 * try (Git git = Git.open(repoDir);
 *      Repository wt = git.worktreeAdd()
 *              .setPath(new File("../feature-wt"))
 *              .setNewBranch("feature")
 *              .call()) {
 *     // use wt
 * }
 * </pre>
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree" >Git documentation about
 *      worktrees</a>
 * @since 7.8
 */
public class WorktreeAddCommand extends GitCommand<Repository> {

	private File path;

	private String name;

	private String startPoint;

	private String newBranch;

	private boolean forceNewBranch;

	private boolean detach;

	private boolean force;

	private boolean checkout = true;

	private boolean lock;

	private String lockReason;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	/**
	 * Constructor for WorktreeAddCommand.
	 *
	 * @param repo
	 *            the repository to work with
	 */
	public WorktreeAddCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the path of the new worktree (required).
	 *
	 * @param path
	 *            destination directory for the new worktree
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setPath(File path) {
		this.path = path;
		return this;
	}

	/**
	 * Override the name used for the admin directory under
	 * {@code .git/worktrees/}. Defaults to the last component of the path.
	 *
	 * @param name
	 *            admin-directory name (must not contain path separators)
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Set the commit or branch to check out in the new worktree.
	 *
	 * @param startPoint
	 *            a commit-ish or branch name; defaults to {@code HEAD} if not
	 *            set and no new branch is created
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setStartPoint(String startPoint) {
		this.startPoint = startPoint;
		return this;
	}

	/**
	 * Create a new branch with the given name and check it out in the new
	 * worktree (equivalent to {@code git worktree add -b}).
	 *
	 * @param branchName
	 *            name of the new branch (short form, e.g. {@code feature})
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setNewBranch(String branchName) {
		this.newBranch = branchName;
		return this;
	}

	/**
	 * Reset the branch if it already exists (equivalent to
	 * {@code git worktree add -B}).
	 *
	 * @param force
	 *            {@code true} to reset the branch if it already exists
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setForceNewBranch(boolean force) {
		this.forceNewBranch = force;
		return this;
	}

	/**
	 * Check out a commit in detached HEAD state (equivalent to
	 * {@code git worktree add --detach}).
	 *
	 * @param detach
	 *            {@code true} to detach HEAD
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setDetach(boolean detach) {
		this.detach = detach;
		return this;
	}

	/**
	 * Allow checking out a branch that is already checked out elsewhere
	 * (equivalent to {@code git worktree add --force}).
	 *
	 * @param force
	 *            {@code true} to bypass the already-checked-out guard
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setForce(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * Skip the checkout step (equivalent to {@code git worktree add
	 * --no-checkout}).
	 *
	 * @param checkout
	 *            {@code false} to skip checkout
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setCheckout(boolean checkout) {
		this.checkout = checkout;
		return this;
	}

	/**
	 * Lock the worktree immediately after creation (equivalent to
	 * {@code git worktree add --lock}).
	 *
	 * @param lock
	 *            {@code true} to lock the worktree
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setLock(boolean lock) {
		this.lock = lock;
		return this;
	}

	/**
	 * Set the reason written to the {@code locked} file.
	 *
	 * @param lockReason
	 *            human-readable lock reason; may be {@code null} or empty
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setLockReason(String lockReason) {
		this.lockReason = lockReason;
		return this;
	}

	/**
	 * Set the progress monitor for the checkout step.
	 *
	 * @param monitor
	 *            a progress monitor; {@code null} resets to
	 *            {@link NullProgressMonitor}
	 * @return {@code this}
	 * @since 7.8
	 */
	public WorktreeAddCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = (monitor != null) ? monitor : NullProgressMonitor.INSTANCE;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Execute the {@code worktree add} command. The returned {@link Repository}
	 * is already open; the caller must close it when done.
	 *
	 * @return an open {@link Repository} for the newly created worktree
	 * @throws GitAPIException
	 *             if the worktree cannot be created
	 */
	@Override
	public Repository call() throws GitAPIException {
		checkCallable();
		try {
			return doAdd();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			setCallable(false);
		}
	}

	private Repository doAdd() throws IOException, GitAPIException {
		// --- 1. Validate path ---
		if (path == null) {
			throw new JGitInternalException(JGitText.get().worktreePathRequired);
		}
		File wtPath = path.getAbsoluteFile();
		if (wtPath.exists() && wtPath.isDirectory()) {
			String[] children = wtPath.list();
			if (children != null && children.length > 0 && !force) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().worktreePathExists, wtPath));
			}
		}

		// --- 2. Determine admin-dir name ---
		String adminName = (name != null && !name.isEmpty())
				? name
				: wtPath.getName();

		File commonDir = repo.getCommonDirectory();
		File worktreesDir = new File(commonDir, Constants.WORKTREES);
		File adminDir = new File(worktreesDir, adminName);
		if (adminDir.exists()) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeNameAlreadyExists, adminName));
		}

		// --- 3. Resolve start commit ---
		String startRef = (startPoint != null) ? startPoint : Constants.HEAD;
		ObjectId startId;
		try {
			startId = repo.resolve(startRef);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeStartPointInvalid, startRef), e);
		}
		if (startId == null) {
			throw new RefNotFoundException(MessageFormat.format(
					JGitText.get().worktreeStartPointInvalid, startRef));
		}

		RevCommit startCommit;
		try (RevWalk rw = new RevWalk(repo)) {
			startCommit = rw.parseCommit(startId);
		} catch (Exception e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().worktreeStartPointInvalid, startRef), e);
		}

		// --- 4. Determine branch to check out ---
		String branchRefName = null; // null = detached
		if (detach) {
			branchRefName = null;
		} else if (newBranch != null && !newBranch.isEmpty()) {
			branchRefName = Constants.R_HEADS + newBranch;
			createBranch(newBranch, startCommit, forceNewBranch);
		} else if (startPoint != null) {
			// Check if startPoint is a branch name
			Ref r = repo.findRef(startPoint);
			if (r != null && r.getName().startsWith(Constants.R_HEADS)) {
				branchRefName = r.getName();
			}
			// else detached
		}

		// --- 5. Check if branch already checked out elsewhere ---
		if (branchRefName != null && !force) {
			if (Worktrees.isBranchCheckedOut(repo, branchRefName, adminName)) {
				String lockedWorktree = findWorktreeForBranch(branchRefName);
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().worktreeBranchAlreadyCheckedOut,
						branchRefName, lockedWorktree));
			}
		}

		// --- 6. Create admin directory structure ---
		adminDir.mkdirs();

		// worktrees/<name>/gitdir — absolute path to <wt>/.git
		File wtDotGit = new File(wtPath, Constants.DOT_GIT);
		writeFile(new File(adminDir, Constants.GITDIR_FILE),
				wtDotGit.getAbsolutePath() + "\n"); //$NON-NLS-1$

		// worktrees/<name>/commondir — relative path from adminDir to commonDir
		String commonRelative = adminDir.toPath()
				.relativize(commonDir.toPath()).toString();
		writeFile(new File(adminDir, Constants.COMMONDIR_FILE),
				commonRelative + "\n"); //$NON-NLS-1$

		// --- 7. Create working tree directory and .git pointer ---
		wtPath.mkdirs();
		writeFile(wtDotGit,
				"gitdir: " + adminDir.getAbsolutePath() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$

		// --- 8. Open worktree Repository ---
		Repository worktreeRepo = new FileRepositoryBuilder()
				.setGitDir(adminDir)
				.setGitCommonDir(commonDir)
				.setWorkTree(wtPath)
				.build();

		try {
			// --- 9. Set HEAD ---
			if (branchRefName != null) {
				RefUpdate ru = worktreeRepo.updateRef(Constants.HEAD);
				ru.setRefLogMessage(
						"worktree add: " + wtPath.getName(), false); //$NON-NLS-1$
				RefUpdate.Result result = ru.link(branchRefName);
				checkRefUpdate(result, branchRefName);
			} else {
				// Detached HEAD
				RefUpdate ru = worktreeRepo.updateRef(Constants.HEAD, true);
				ru.setNewObjectId(startCommit);
				ru.setRefLogMessage(
						"worktree add: " + wtPath.getName(), false); //$NON-NLS-1$
				RefUpdate.Result result = ru.forceUpdate();
				checkRefUpdate(result, startCommit.name());
			}

			// --- 10. Checkout ---
			if (checkout) {
				DirCache dc = worktreeRepo.lockDirCache();
				try {
					DirCacheCheckout dco = new DirCacheCheckout(
							worktreeRepo, null, dc, startCommit.getTree());
					dco.setFailOnConflict(true);
					dco.setProgressMonitor(monitor);
					dco.checkout();
				} finally {
					dc.unlock();
				}
			}

			// --- 11. Lock ---
			if (lock) {
				Worktrees.writeLocked(adminDir, lockReason);
			}

		} catch (Exception e) {
			// On failure, clean up created directories before rethrowing
			worktreeRepo.close();
			deleteRecursive(adminDir);
			deleteRecursive(wtPath);
			if (e instanceof GitAPIException) {
				throw (GitAPIException) e;
			}
			throw new JGitInternalException(e.getMessage(), e);
		}

		return worktreeRepo;
	}

	private void createBranch(String branchName, RevCommit startCommit,
			boolean reset) throws GitAPIException {
		CreateBranchCommand create = new CreateBranchCommand(repo);
		create.setName(branchName);
		create.setStartPoint(startCommit);
		if (reset) {
			create.setForce(true);
		}
		try {
			create.call();
		} catch (RefAlreadyExistsException e) {
			if (!reset) {
				throw e;
			}
			// forceNewBranch: already handled by setForce(true) above
		}
	}

	private String findWorktreeForBranch(String branchRef) throws IOException {
		// Best-effort: find which worktree holds this branch (for error message)
		Ref mainHead = repo.exactRef(Constants.HEAD);
		if (mainHead != null
				&& branchRef.equals(mainHead.getTarget().getName())) {
			return "(main)"; //$NON-NLS-1$
		}
		File worktreesDir = new File(repo.getCommonDirectory(),
				Constants.WORKTREES);
		if (worktreesDir.isDirectory()) {
			File[] entries = worktreesDir.listFiles();
			if (entries != null) {
				for (File ad : entries) {
					if (!ad.isDirectory()) {
						continue;
					}
					String[] headInfo = Worktrees.readHead(ad);
					if (branchRef.equals(headInfo[1])) {
						return ad.getName();
					}
				}
			}
		}
		return "(unknown)"; //$NON-NLS-1$
	}

	private static void checkRefUpdate(RefUpdate.Result result, String ref)
			throws JGitInternalException {
		switch (result) {
		case NEW:
		case FORCED:
		case NO_CHANGE:
			return;
		default:
			throw new JGitInternalException(
					"Unexpected ref-update result " + result + " for " + ref); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void writeFile(File f, String content) throws IOException {
		Files.write(f.toPath(), content.getBytes(UTF_8));
	}

	private static void deleteRecursive(File f) {
		if (f == null || !f.exists()) {
			return;
		}
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}
		f.delete();
	}
}
