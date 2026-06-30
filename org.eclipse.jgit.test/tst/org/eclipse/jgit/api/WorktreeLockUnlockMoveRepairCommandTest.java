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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.worktree.WorktreeReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link WorktreeLockCommand}, {@link WorktreeUnlockCommand},
 * {@link WorktreeMoveCommand}, and {@link WorktreeRepairCommand}.
 */
public class WorktreeLockUnlockMoveRepairCommandTest extends RepositoryTestCase {

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content"); //$NON-NLS-1$
			git.add().addFilepattern("file.txt").call(); //$NON-NLS-1$
			git.commit().setMessage("Initial commit").call(); //$NON-NLS-1$
		}
	}

	// -----------------------------------------------------------------------
	// WorktreeLockCommand tests
	// -----------------------------------------------------------------------

	/** Locking a worktree creates the locked file. */
	@Test
	public void testLockCreatesLockedFile() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-lock"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("lock-branch") //$NON-NLS-1$
				.call()) {
			// created
		}

		new WorktreeLockCommand(db).setName("wt-lock").call(); //$NON-NLS-1$

		File lockedFile = new File(db.getDirectory(), "worktrees/wt-lock/locked"); //$NON-NLS-1$
		assertTrue("locked file must exist", lockedFile.exists()); //$NON-NLS-1$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();
		boolean found = false;
		for (WorktreeReference ref : wts) {
			if ("wt-lock".equals(ref.getName())) { //$NON-NLS-1$
				assertTrue(ref.isLocked());
				found = true;
			}
		}
		assertTrue("worktree wt-lock must appear in list", found); //$NON-NLS-1$
	}

	/** Locking with a reason writes the reason into the locked file. */
	@Test
	public void testLockWithReason() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-lock-reason"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("lock-reason-branch") //$NON-NLS-1$
				.call()) {
			// created
		}

		new WorktreeLockCommand(db).setName("wt-lock-reason").setReason("CI hold").call(); //$NON-NLS-1$ //$NON-NLS-2$

		List<WorktreeReference> wts = new WorktreeListCommand(db).call();
		for (WorktreeReference ref : wts) {
			if ("wt-lock-reason".equals(ref.getName())) { //$NON-NLS-1$
				assertEquals("CI hold", ref.getLockReason()); //$NON-NLS-1$
			}
		}
	}

	/** Locking an already-locked worktree is rejected. */
	@Test
	public void testLockAlreadyLockedRejected() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-already-locked"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("already-locked-branch") //$NON-NLS-1$
				.setLock(true)
				.call()) {
			// already locked
		}

		try {
			new WorktreeLockCommand(db).setName("wt-already-locked").call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for already-locked worktree"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("wt-already-locked")); //$NON-NLS-1$
		}
	}

	/** Locking an unknown worktree name throws an exception. */
	@Test
	public void testLockUnknownName() throws Exception {
		try {
			new WorktreeLockCommand(db).setName("nonexistent").call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for unknown worktree"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("nonexistent")); //$NON-NLS-1$
		}
	}

	// -----------------------------------------------------------------------
	// WorktreeUnlockCommand tests
	// -----------------------------------------------------------------------

	/** Unlocking a locked worktree removes the locked file. */
	@Test
	public void testUnlockRemovesLockedFile() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-unlock"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("unlock-branch") //$NON-NLS-1$
				.setLock(true)
				.call()) {
			// locked
		}

		new WorktreeUnlockCommand(db).setName("wt-unlock").call(); //$NON-NLS-1$

		File lockedFile = new File(db.getDirectory(), "worktrees/wt-unlock/locked"); //$NON-NLS-1$
		assertFalse("locked file must be removed", lockedFile.exists()); //$NON-NLS-1$
	}

	/** Unlocking a worktree that is not locked is rejected. */
	@Test
	public void testUnlockNotLockedRejected() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-not-locked"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("not-locked-branch") //$NON-NLS-1$
				.call()) {
			// not locked
		}

		try {
			new WorktreeUnlockCommand(db).setName("wt-not-locked").call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for not-locked worktree"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("wt-not-locked")); //$NON-NLS-1$
		}
	}

	// -----------------------------------------------------------------------
	// WorktreeMoveCommand tests
	// -----------------------------------------------------------------------

	/** Moving a worktree updates both the gitdir file and the .git pointer. */
	@Test
	public void testMoveUpdatesPointers() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-move-src"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("move-branch") //$NON-NLS-1$
				.call()) {
			// created
		}

		File dest = new File(db.getWorkTree().getParentFile(), "wt-move-dest"); //$NON-NLS-1$
		new WorktreeMoveCommand(db).setName("wt-move-src").setNewPath(dest).call(); //$NON-NLS-1$

		// Old path must not exist
		assertFalse("old working tree must not exist", wtPath.exists()); //$NON-NLS-1$
		// New path must exist
		assertTrue("new working tree must exist", dest.exists()); //$NON-NLS-1$

		// .git pointer in new path must point at admin dir
		File dotGit = new File(dest, Constants.DOT_GIT);
		assertTrue(".git pointer must exist in new path", dotGit.exists()); //$NON-NLS-1$
		String dotGitContent = new String(
				java.nio.file.Files.readAllBytes(dotGit.toPath()),
				java.nio.charset.StandardCharsets.UTF_8);
		File adminDir = new File(db.getDirectory(), "worktrees/wt-move-src"); //$NON-NLS-1$
		assertTrue(".git must reference admin dir", //$NON-NLS-1$
				dotGitContent.contains(adminDir.getAbsolutePath()));

		// gitdir file in admin dir must point at new .git
		File gitdirFile = new File(adminDir, Constants.GITDIR_FILE);
		String gitdirContent = new String(
				java.nio.file.Files.readAllBytes(gitdirFile.toPath()),
				java.nio.charset.StandardCharsets.UTF_8);
		assertTrue("gitdir must reference new .git path", //$NON-NLS-1$
				gitdirContent.contains(dotGit.getAbsolutePath()));
	}

	/** Moving a locked worktree without --force is rejected. */
	@Test
	public void testMoveLockedRejected() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-move-locked"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("move-locked-branch") //$NON-NLS-1$
				.setLock(true)
				.call()) {
			// locked
		}

		File dest = new File(db.getWorkTree().getParentFile(), "wt-move-locked-dest"); //$NON-NLS-1$
		try {
			new WorktreeMoveCommand(db).setName("wt-move-locked").setNewPath(dest).call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for locked worktree"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("wt-move-locked")); //$NON-NLS-1$
		}

		// Old path must still exist
		assertTrue(wtPath.exists());
	}

	/** Moving a locked worktree with --force succeeds. */
	@Test
	public void testMoveLockedWithForce() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-move-force"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("move-force-branch") //$NON-NLS-1$
				.setLock(true)
				.call()) {
			// locked
		}

		File dest = new File(db.getWorkTree().getParentFile(), "wt-move-force-dest"); //$NON-NLS-1$
		new WorktreeMoveCommand(db).setName("wt-move-force").setNewPath(dest) //$NON-NLS-1$
				.setForce(true).call();

		assertFalse("old path must not exist after forced move", wtPath.exists()); //$NON-NLS-1$
		assertTrue("new path must exist after forced move", dest.exists()); //$NON-NLS-1$
	}

	// -----------------------------------------------------------------------
	// WorktreeRepairCommand tests
	// -----------------------------------------------------------------------

	/**
	 * Repair rewrites both the gitdir and .git pointer after a simulated
	 * manual move.
	 */
	@Test
	public void testRepairAfterManualMove() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-repair-src"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("repair-branch") //$NON-NLS-1$
				.call()) {
			// created
		}

		// Simulate a manual move: rename the working tree without updating the pointers
		File movedPath = new File(db.getWorkTree().getParentFile(), "wt-repair-moved"); //$NON-NLS-1$
		assertTrue("rename must succeed", wtPath.renameTo(movedPath)); //$NON-NLS-1$

		// Now run repair to fix the pointers
		List<String> repaired = new WorktreeRepairCommand(db)
				.addPath("wt-repair-src", movedPath) //$NON-NLS-1$
				.call();

		assertEquals(1, repaired.size());
		assertEquals("wt-repair-src", repaired.get(0)); //$NON-NLS-1$

		// Verify pointer pair is consistent
		File adminDir = new File(db.getDirectory(), "worktrees/wt-repair-src"); //$NON-NLS-1$
		File dotGit = new File(movedPath, Constants.DOT_GIT);
		assertTrue(".git must exist in moved path", dotGit.exists()); //$NON-NLS-1$

		String dotGitContent = new String(
				java.nio.file.Files.readAllBytes(dotGit.toPath()),
				java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(".git must point at admin dir", //$NON-NLS-1$
				dotGitContent.contains(adminDir.getAbsolutePath()));

		File gitdirFile = new File(adminDir, Constants.GITDIR_FILE);
		String gitdirContent = new String(
				java.nio.file.Files.readAllBytes(gitdirFile.toPath()),
				java.nio.charset.StandardCharsets.UTF_8);
		assertTrue("gitdir must point at .git in moved path", //$NON-NLS-1$
				gitdirContent.contains(dotGit.getAbsolutePath()));
	}

	/** Repairing with no paths does nothing and returns an empty list. */
	@Test
	public void testRepairNoPaths() throws Exception {
		List<String> repaired = new WorktreeRepairCommand(db).call();
		assertTrue(repaired.isEmpty());
	}

	/** Repairing an unknown worktree name throws an exception. */
	@Test
	public void testRepairUnknownName() throws Exception {
		File fakePath = new File(db.getWorkTree().getParentFile(), "wt-repair-fake"); //$NON-NLS-1$
		fakePath.mkdirs();
		try {
			new WorktreeRepairCommand(db).addPath("nonexistent", fakePath).call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for unknown worktree"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("nonexistent")); //$NON-NLS-1$
		}
	}
}
