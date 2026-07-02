/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.worktree;

import java.io.File;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Immutable description of a linked worktree entry as read from
 * {@code .git/worktrees/<name>/}.
 *
 * <p>
 * A {@code WorktreeReference} corresponds to one entry returned by
 * {@code git worktree list}. The main worktree is always included as the first
 * entry; its {@link #getName()} returns an empty string and its
 * {@link #isMain()} returns {@code true}.
 *
 * <p>
 * Use {@link org.eclipse.jgit.api.WorktreeListCommand} to obtain instances.
 *
 * @since 7.8
 */
public class WorktreeReference {

	private final String name;

	private final File worktreePath;

	private final File gitDir;

	private final ObjectId head;

	private final String branch;

	private final boolean detached;

	private final boolean bare;

	private final boolean locked;

	private final String lockReason;

	private final boolean prunable;

	private final String prunableReason;

	/**
	 * Builder for {@link WorktreeReference}.
	 *
	 * @since 7.8
	 */
	public static final class Builder {

		private String name = ""; //$NON-NLS-1$

		private File worktreePath;

		private File gitDir;

		private ObjectId head;

		private String branch;

		private boolean detached;

		private boolean bare;

		private boolean locked;

		private String lockReason;

		private boolean prunable;

		private String prunableReason;

		/** Create an empty builder. */
		public Builder() {
			// nothing
		}

		/**
		 * Set the internal name (basename of
		 * {@code .git/worktrees/<name>}); empty string for the main
		 * worktree.
		 *
		 * @param n
		 *            name
		 * @return {@code this}
		 */
		public Builder setName(String n) {
			this.name = n;
			return this;
		}

		/**
		 * Set the absolute path of the working tree directory.
		 *
		 * @param p
		 *            working tree path
		 * @return {@code this}
		 */
		public Builder setWorktreePath(File p) {
			this.worktreePath = p;
			return this;
		}

		/**
		 * Set the per-worktree git directory
		 * ({@code .git/worktrees/<name>}).
		 *
		 * @param d
		 *            git directory
		 * @return {@code this}
		 */
		public Builder setGitDir(File d) {
			this.gitDir = d;
			return this;
		}

		/**
		 * Set the HEAD commit object id; {@code null} for an unborn HEAD.
		 *
		 * @param h
		 *            HEAD object id
		 * @return {@code this}
		 */
		public Builder setHead(ObjectId h) {
			this.head = h;
			return this;
		}

		/**
		 * Set the full ref name of the checked-out branch
		 * (e.g. {@code refs/heads/main}); {@code null} for a detached
		 * HEAD.
		 *
		 * @param b
		 *            branch ref name
		 * @return {@code this}
		 */
		public Builder setBranch(String b) {
			this.branch = b;
			return this;
		}

		/**
		 * Mark the worktree as having a detached HEAD.
		 *
		 * @param d
		 *            detached flag
		 * @return {@code this}
		 */
		public Builder setDetached(boolean d) {
			this.detached = d;
			return this;
		}

		/**
		 * Mark the worktree as bare.
		 *
		 * @param b
		 *            bare flag
		 * @return {@code this}
		 */
		public Builder setBare(boolean b) {
			this.bare = b;
			return this;
		}

		/**
		 * Set the locked flag.
		 *
		 * @param l
		 *            locked flag
		 * @return {@code this}
		 */
		public Builder setLocked(boolean l) {
			this.locked = l;
			return this;
		}

		/**
		 * Set the human-readable lock reason (may be {@code null} or empty
		 * when the {@code locked} file exists but is empty).
		 *
		 * @param r
		 *            lock reason
		 * @return {@code this}
		 */
		public Builder setLockReason(String r) {
			this.lockReason = r;
			return this;
		}

		/**
		 * Set the prunable flag.
		 *
		 * @param p
		 *            prunable flag
		 * @return {@code this}
		 */
		public Builder setPrunable(boolean p) {
			this.prunable = p;
			return this;
		}

		/**
		 * Set the human-readable reason why this worktree is prunable.
		 *
		 * @param r
		 *            prunable reason
		 * @return {@code this}
		 */
		public Builder setPrunableReason(String r) {
			this.prunableReason = r;
			return this;
		}

		/**
		 * Build the immutable {@link WorktreeReference}.
		 *
		 * @return new {@link WorktreeReference}
		 */
		public WorktreeReference build() {
			return new WorktreeReference(this);
		}
	}

	private WorktreeReference(Builder b) {
		this.name = b.name;
		this.worktreePath = b.worktreePath;
		this.gitDir = b.gitDir;
		this.head = b.head;
		this.branch = b.branch;
		this.detached = b.detached;
		this.bare = b.bare;
		this.locked = b.locked;
		this.lockReason = b.lockReason;
		this.prunable = b.prunable;
		this.prunableReason = b.prunableReason;
	}

	/**
	 * Get the internal name of this worktree.
	 *
	 * <p>
	 * For a linked worktree this is the basename of
	 * {@code .git/worktrees/<name>}. For the main worktree the empty string
	 * is returned.
	 *
	 * @return internal name; never {@code null}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the absolute path of the working tree directory.
	 *
	 * @return working tree path; may be {@code null} for a bare worktree
	 */
	public File getWorktreePath() {
		return worktreePath;
	}

	/**
	 * Get the per-worktree git directory.
	 *
	 * <p>
	 * For the main worktree this is the top-level {@code .git} directory. For
	 * a linked worktree it is {@code .git/worktrees/<name>}.
	 *
	 * @return git directory; may be {@code null}
	 */
	public File getGitDir() {
		return gitDir;
	}

	/**
	 * Get the HEAD commit object id.
	 *
	 * @return HEAD object id; {@code null} for an unborn HEAD
	 */
	public ObjectId getHead() {
		return head;
	}

	/**
	 * Get the full ref name of the checked-out branch.
	 *
	 * @return full ref name (e.g. {@code refs/heads/main}); {@code null} for
	 *         a detached HEAD
	 */
	public String getBranch() {
		return branch;
	}

	/**
	 * Whether the HEAD is detached (not pointing to a branch).
	 *
	 * @return {@code true} if the HEAD is detached
	 */
	public boolean isDetached() {
		return detached;
	}

	/**
	 * Whether this is a bare worktree (no working tree directory).
	 *
	 * @return {@code true} if the worktree is bare
	 */
	public boolean isBare() {
		return bare;
	}

	/**
	 * Whether this worktree is locked.
	 *
	 * <p>
	 * A locked worktree cannot be removed or pruned without {@code --force}.
	 *
	 * @return {@code true} if locked
	 */
	public boolean isLocked() {
		return locked;
	}

	/**
	 * Get the human-readable lock reason.
	 *
	 * @return lock reason; {@code null} or empty when the worktree is locked
	 *         without an explicit reason
	 */
	public String getLockReason() {
		return lockReason;
	}

	/**
	 * Whether this worktree is prunable.
	 *
	 * <p>
	 * A worktree is prunable when its working-tree path no longer exists or
	 * its {@code gitdir} back-link is stale. It will be removed by
	 * {@code git worktree prune} unless locked.
	 *
	 * @return {@code true} if prunable
	 */
	public boolean isPrunable() {
		return prunable;
	}

	/**
	 * Get the human-readable reason why this worktree is prunable.
	 *
	 * @return prunable reason; {@code null} when not prunable
	 */
	public String getPrunableReason() {
		return prunableReason;
	}

	/**
	 * Whether this is the main worktree (the repository itself).
	 *
	 * @return {@code true} for the main worktree
	 */
	public boolean isMain() {
		return name.isEmpty();
	}

	@Override
	public String toString() {
		return "WorktreeReference[name=" + name + ", path=" + worktreePath //$NON-NLS-1$ //$NON-NLS-2$
				+ ", head=" + head + ", branch=" + branch //$NON-NLS-1$ //$NON-NLS-2$
				+ ", locked=" + locked + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
