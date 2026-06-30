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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.worktree.WorktreeReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link WorktreeAddCommand}.
 */
public class WorktreeAddCommandTest extends RepositoryTestCase {

	private ObjectId firstCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content"); //$NON-NLS-1$
			git.add().addFilepattern("file.txt").call(); //$NON-NLS-1$
			firstCommit = git.commit().setMessage("Initial commit").call() //$NON-NLS-1$
					.getId();
		}
	}

	/** A basic add creates the directory structure and is visible from list. */
	@Test
	public void testAddCreatesWorktree() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-add"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("feature") //$NON-NLS-1$
				.call()) {

			assertNotNull(wt);
			assertTrue("working tree directory must exist", wtPath.isDirectory()); //$NON-NLS-1$

			File dotGit = new File(wtPath, Constants.DOT_GIT);
			assertTrue(".git pointer must exist", dotGit.isFile()); //$NON-NLS-1$

			File adminDir = new File(db.getDirectory(),
					"worktrees/wt-add"); //$NON-NLS-1$
			assertTrue("admin dir must exist", adminDir.isDirectory()); //$NON-NLS-1$
			assertTrue("gitdir file must exist", //$NON-NLS-1$
					new File(adminDir, "gitdir").isFile()); //$NON-NLS-1$
			assertTrue("commondir file must exist", //$NON-NLS-1$
					new File(adminDir, "commondir").isFile()); //$NON-NLS-1$
			assertTrue("HEAD file must exist", //$NON-NLS-1$
					new File(adminDir, "HEAD").isFile()); //$NON-NLS-1$
		}
	}

	/** After add, worktreeList sees 2 worktrees (main + linked). */
	@Test
	public void testAddVisibleFromList() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-list"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("list-branch") //$NON-NLS-1$
				.call()) {

			List<WorktreeReference> wts = new WorktreeListCommand(db).call();
			assertEquals(2, wts.size());
			assertTrue(wts.get(0).isMain());

			WorktreeReference linked = wts.get(1);
			assertEquals("wt-list", linked.getName()); //$NON-NLS-1$
			assertEquals("refs/heads/list-branch", linked.getBranch()); //$NON-NLS-1$
			assertNotNull(linked.getHead());
			assertEquals(firstCommit, linked.getHead());
			assertFalse(linked.isDetached());
			assertFalse(linked.isLocked());
		}
	}

	/** HEAD of the returned worktree Repository points to the new branch. */
	@Test
	public void testWorktreeRepositoryHead() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-head"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("head-branch") //$NON-NLS-1$
				.call()) {

			Ref head = wt.exactRef(Constants.HEAD);
			assertNotNull(head);
			assertTrue("HEAD must be symbolic", head.isSymbolic()); //$NON-NLS-1$
			assertEquals("refs/heads/head-branch", //$NON-NLS-1$
					head.getTarget().getName());
		}
	}

	/** The branch created by add exists in the common ref-db. */
	@Test
	public void testBranchCreatedInCommonRepo() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-branch"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("common-branch") //$NON-NLS-1$
				.call()) {

			Ref branch = db.findRef("common-branch"); //$NON-NLS-1$
			assertNotNull("branch must exist in common repo", branch); //$NON-NLS-1$
			assertEquals(firstCommit, branch.getObjectId());
		}
	}

	/** Detached HEAD checkout: no branch, HEAD points to the commit. */
	@Test
	public void testDetachedHead() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-detach"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setStartPoint(firstCommit.getName())
				.setDetach(true)
				.call()) {

			List<WorktreeReference> wts = new WorktreeListCommand(db).call();
			WorktreeReference linked = wts.get(1);
			assertTrue(linked.isDetached()); //$NON-NLS-1$
			assertEquals(firstCommit, linked.getHead());
		}
	}

	/** Add with --lock writes the locked file immediately. */
	@Test
	public void testAddWithLock() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-locked"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("locked-branch") //$NON-NLS-1$
				.setLock(true)
				.setLockReason("held by test") //$NON-NLS-1$
				.call()) {

			List<WorktreeReference> wts = new WorktreeListCommand(db).call();
			WorktreeReference linked = wts.get(1);
			assertTrue(linked.isLocked());
			assertEquals("held by test", linked.getLockReason()); //$NON-NLS-1$
		}
	}

	/** Duplicate worktree name is rejected. */
	@Test
	public void testDuplicateNameRejected() throws Exception {
		File wtPath1 = new File(db.getWorkTree().getParentFile(), "wt-dup"); //$NON-NLS-1$
		File wtPath2 = new File(db.getWorkTree().getParentFile(), "wt-dup2"); //$NON-NLS-1$

		try (Repository wt1 = new WorktreeAddCommand(db)
				.setPath(wtPath1)
				.setNewBranch("dup-branch1") //$NON-NLS-1$
				.call()) {
			// Second add with the same admin name (derived from path basename)
			// but a different physical path — override name to force collision
			try {
				new WorktreeAddCommand(db)
						.setPath(wtPath2)
						.setName("wt-dup") //$NON-NLS-1$
						.setNewBranch("dup-branch2") //$NON-NLS-1$
						.call()
						.close();
				fail("Expected JGitInternalException for duplicate name"); //$NON-NLS-1$
			} catch (org.eclipse.jgit.api.errors.JGitInternalException e) {
				assertTrue(e.getMessage().contains("wt-dup")); //$NON-NLS-1$
			}
		}
	}

	/** No-checkout: files are NOT checked out but HEAD is set. */
	@Test
	public void testNoCheckout() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-noco"); //$NON-NLS-1$

		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("noco-branch") //$NON-NLS-1$
				.setCheckout(false)
				.call()) {

			// HEAD must still point to the branch
			Ref head = wt.exactRef(Constants.HEAD);
			assertNotNull(head);
			assertTrue(head.isSymbolic());

			// But the file must not be checked out
			assertFalse("file.txt must not be checked out with --no-checkout", //$NON-NLS-1$
					new File(wtPath, "file.txt").exists()); //$NON-NLS-1$
		}
	}

	/** The main repo HEAD is unchanged after add. */
	@Test
	public void testMainHeadUnchanged() throws Exception {
		Ref mainHeadBefore = db.exactRef(Constants.HEAD);
		String mainBranchBefore = mainHeadBefore.getTarget().getName();

		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-main"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("other-branch") //$NON-NLS-1$
				.call()) {
			// no-op on main
		}

		Ref mainHeadAfter = db.exactRef(Constants.HEAD);
		assertEquals("main HEAD must not change", //$NON-NLS-1$
				mainBranchBefore, mainHeadAfter.getTarget().getName());
	}
}
