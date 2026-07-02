/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.WorktreeAddCommand;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that per-worktree pseudo-refs (ORIG_HEAD, MERGE_HEAD, …) are
 * stored in and read from the worktree's own git directory, isolated from
 * both the common directory and other worktrees.
 *
 * <p>All tests are pure-JGit and do not require the native git binary.
 *
 * @see RefDirectory#isPerWorktreeRef(String)
 */
public class PerWorktreeRefTest extends RepositoryTestCase {

	private RevCommit initialCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "initial"); //$NON-NLS-1$
			git.add().addFilepattern("file.txt").call(); //$NON-NLS-1$
			initialCommit = git.commit().setMessage("Initial commit").call(); //$NON-NLS-1$
		}
	}

	// -----------------------------------------------------------------------
	// isPerWorktreePseudoRef() contract
	// -----------------------------------------------------------------------

	/** HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.HEAD));
	}

	/** ORIG_HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testOrigHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.ORIG_HEAD));
	}

	/** MERGE_HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testMergeHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.MERGE_HEAD));
	}

	/** CHERRY_PICK_HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testCherryPickHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.CHERRY_PICK_HEAD));
	}

	/** REVERT_HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testRevertHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.REVERT_HEAD));
	}

	/** BISECT_HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testBisectHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.BISECT_HEAD));
	}

	/** FETCH_HEAD is a per-worktree pseudo-ref. */
	@Test
	public void testFetchHeadIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreePseudoRef(Constants.FETCH_HEAD));
	}

	/**
	 * COMMIT_EDITMSG is NOT a per-worktree ref (must not be accidentally
	 * caught by any "all-uppercase" heuristic).
	 */
	@Test
	public void testCommitEditMsgIsNotPerWorktree() {
		assertFalse(RefDirectory.isPerWorktreePseudoRef("COMMIT_EDITMSG")); //$NON-NLS-1$
	}

	/** MERGE_MSG is NOT a per-worktree ref. */
	@Test
	public void testMergeMsgIsNotPerWorktree() {
		assertFalse(RefDirectory.isPerWorktreePseudoRef("MERGE_MSG")); //$NON-NLS-1$
	}

	/** refs/worktree/* refs are per-worktree. */
	@Test
	public void testWorktreePrefixIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreeRef(Constants.R_WORKTREE + "foo")); //$NON-NLS-1$
	}

	/** refs/bisect/* refs are per-worktree. */
	@Test
	public void testBisectPrefixIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreeRef(Constants.R_BISECT + "state")); //$NON-NLS-1$
	}

	/** refs/rewritten/* refs are per-worktree. */
	@Test
	public void testRewrittenPrefixIsPerWorktree() {
		assertTrue(RefDirectory.isPerWorktreeRef(
				Constants.R_REWRITTEN + "pick/abc123")); //$NON-NLS-1$
	}

	/** refs/heads/* refs are shared, not per-worktree. */
	@Test
	public void testBranchRefIsNotPerWorktree() {
		assertFalse(RefDirectory.isPerWorktreeRef(Constants.R_HEADS + "main")); //$NON-NLS-1$
	}

	/**
	 * refs/worktree/* written in a linked worktree must be stored in the
	 * worktree's own git directory, not the common directory.
	 */
	@Test
	public void testWorktreePrefixStoredInWorktreeGitDir() throws Exception {
		String refName = Constants.R_WORKTREE + "example"; //$NON-NLS-1$
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-prefix"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("wt-prefix-branch") //$NON-NLS-1$
				.call()) {

			RefUpdate u = wt.updateRef(refName);
			u.setNewObjectId(initialCommit.getId());
			u.setForceUpdate(true);
			Result result = u.forceUpdate();
			assertTrue("refs/worktree ref update must succeed, got: " + result, //$NON-NLS-1$
					result == Result.NEW || result == Result.FORCED);

			File expected = new File(new File(wt.getDirectory(), Constants.R_REFS),
					"worktree/example"); //$NON-NLS-1$
			assertTrue("refs/worktree ref must be in worktree gitDir", //$NON-NLS-1$
					expected.exists());

			File notExpected = new File(
					new File(wt.getCommonDirectory(), Constants.R_REFS),
					"worktree/example"); //$NON-NLS-1$
			assertFalse("refs/worktree ref must NOT be in common dir", //$NON-NLS-1$
					notExpected.exists());
		}
	}

	// -----------------------------------------------------------------------
	// Physical file isolation
	// -----------------------------------------------------------------------

	/**
	 * Writing ORIG_HEAD in a linked worktree must store the file in the
	 * worktree's own git directory, NOT in the common directory.
	 */
	@Test
	public void testOrigHeadStoredInWorktreeGitDir() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-orig"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("wt-orig-branch") //$NON-NLS-1$
				.call()) {

			// Write ORIG_HEAD into the linked worktree
			RefUpdate u = wt.updateRef(Constants.ORIG_HEAD);
			u.setNewObjectId(initialCommit.getId());
			u.setForceUpdate(true);
			Result result = u.forceUpdate();
			assertTrue("ORIG_HEAD update must succeed, got: " + result, //$NON-NLS-1$
					result == Result.NEW || result == Result.FORCED);

			// The physical file must be in the worktree's git dir
			File expected = new File(wt.getDirectory(), Constants.ORIG_HEAD);
			assertTrue("ORIG_HEAD must be in the worktree gitDir", //$NON-NLS-1$
					expected.exists());

			// The common dir must NOT have an ORIG_HEAD file
			File notExpected = new File(wt.getCommonDirectory(), Constants.ORIG_HEAD);
			assertFalse("ORIG_HEAD must NOT be in the common dir", //$NON-NLS-1$
					notExpected.exists());
		}
	}

	/**
	 * ORIG_HEAD written in worktree A must not be visible when resolving
	 * ORIG_HEAD from the main repository.
	 */
	@Test
	public void testOrigHeadIsolatedFromMainRepo() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-isolated"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("wt-isolated-branch") //$NON-NLS-1$
				.call()) {

			// Write ORIG_HEAD in the worktree
			RefUpdate u = wt.updateRef(Constants.ORIG_HEAD);
			u.setNewObjectId(initialCommit.getId());
			u.setForceUpdate(true);
			u.forceUpdate();

			// Main repo must not see the worktree's ORIG_HEAD
			ObjectId mainOrigHead = db.resolve(Constants.ORIG_HEAD);
			assertNull("Main repo must not see worktree's ORIG_HEAD", mainOrigHead); //$NON-NLS-1$
		}
	}

	/**
	 * ORIG_HEAD written in worktree A must not be visible in worktree B.
	 */
	@Test
	public void testOrigHeadIsolatedBetweenWorktrees() throws Exception {
		File wtAPath = new File(db.getWorkTree().getParentFile(), "wt-a"); //$NON-NLS-1$
		File wtBPath = new File(db.getWorkTree().getParentFile(), "wt-b"); //$NON-NLS-1$

		try (Repository wtA = new WorktreeAddCommand(db)
				.setPath(wtAPath)
				.setNewBranch("wt-a-branch") //$NON-NLS-1$
				.call();
			 Repository wtB = new WorktreeAddCommand(db)
				.setPath(wtBPath)
				.setNewBranch("wt-b-branch") //$NON-NLS-1$
				.call()) {

			// Write ORIG_HEAD in worktree A
			RefUpdate u = wtA.updateRef(Constants.ORIG_HEAD);
			u.setNewObjectId(initialCommit.getId());
			u.setForceUpdate(true);
			u.forceUpdate();

			// Worktree B must not see it
			ObjectId wtBOrigHead = wtB.resolve(Constants.ORIG_HEAD);
			assertNull("Worktree B must not see worktree A's ORIG_HEAD", //$NON-NLS-1$
					wtBOrigHead);
		}
	}

	/**
	 * refs/heads/* must be shared: a ref written from a linked worktree is
	 * visible from the main repo and other worktrees.
	 */
	@Test
	public void testBranchRefIsShared() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-shared"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("shared-branch") //$NON-NLS-1$
				.call()) {

			// The branch is visible from the main repo
			ObjectId mainRef = db.resolve("refs/heads/shared-branch"); //$NON-NLS-1$
			ObjectId wtRef = wt.resolve("refs/heads/shared-branch"); //$NON-NLS-1$

			assertTrue("shared-branch must be visible from main repo", //$NON-NLS-1$
					mainRef != null && !ObjectId.zeroId().equals(mainRef));
			assertEquals("shared-branch must resolve to same commit in both repos", //$NON-NLS-1$
					mainRef, wtRef);
		}
	}

	// -----------------------------------------------------------------------
	// Reflog isolation
	// -----------------------------------------------------------------------

	/**
	 * The reflog for ORIG_HEAD in a linked worktree must be written to the
	 * worktree's own git directory, not the common directory.
	 */
	@Test
	public void testOrigHeadReflogStoredInWorktreeGitDir() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-reflog"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("wt-reflog-branch") //$NON-NLS-1$
				.call()) {

			// Write ORIG_HEAD with a reflog message (forceRefLog required for
			// pseudo-refs other than HEAD)
			RefUpdate u = wt.updateRef(Constants.ORIG_HEAD);
			u.setNewObjectId(initialCommit.getId());
			u.setRefLogMessage("test: per-worktree reflog", false); //$NON-NLS-1$
			u.setForceRefLog(true);
			u.setForceUpdate(true);
			u.forceUpdate();

			// Reflog must be in the worktree's git dir
			File worktreeLog = new File(new File(wt.getDirectory(), Constants.LOGS),
					Constants.ORIG_HEAD);
			assertTrue("ORIG_HEAD reflog must be in worktree gitDir", //$NON-NLS-1$
					worktreeLog.exists());
			String logContent = new String(
					Files.readAllBytes(worktreeLog.toPath()), StandardCharsets.UTF_8);
			assertTrue("Reflog must contain the message", //$NON-NLS-1$
					logContent.contains("test: per-worktree reflog")); //$NON-NLS-1$

			// Reflog must NOT be in the common dir
			File commonLog = new File(new File(wt.getCommonDirectory(), Constants.LOGS),
					Constants.ORIG_HEAD);
			assertFalse("ORIG_HEAD reflog must NOT be in common dir", //$NON-NLS-1$
					commonLog.exists());
		}
	}
}
