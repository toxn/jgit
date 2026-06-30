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
 * Unit tests for {@link WorktreeRemoveCommand} and
 * {@link WorktreePruneCommand}.
 */
public class WorktreeRemovePruneCommandTest extends RepositoryTestCase {

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
	// WorktreeRemoveCommand tests
	// -----------------------------------------------------------------------

	/** A clean worktree can be removed: admin dir and working tree deleted. */
	@Test
	public void testRemoveCleanWorktree() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-remove"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("remove-branch") //$NON-NLS-1$
				.call()) {
			// ensure it shows up in list
			assertEquals(2, new WorktreeListCommand(db).call().size());
		}

		new WorktreeRemoveCommand(db).setName("wt-remove").call(); //$NON-NLS-1$

		assertFalse("working tree must be deleted", wtPath.exists()); //$NON-NLS-1$
		assertFalse("admin dir must be deleted", //$NON-NLS-1$
				new File(db.getDirectory(), "worktrees/wt-remove").exists()); //$NON-NLS-1$
		assertEquals(1, new WorktreeListCommand(db).call().size());
	}

	/** Removing a locked worktree without --force is rejected. */
	@Test
	public void testRemoveLockedWorktreeRejected() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-locked-rm"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("locked-rm-branch") //$NON-NLS-1$
				.setLock(true)
				.setLockReason("held") //$NON-NLS-1$
				.call()) {
			// locked
		}

		try {
			new WorktreeRemoveCommand(db).setName("wt-locked-rm").call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for locked worktree"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("wt-locked-rm")); //$NON-NLS-1$
		}

		// Still exists
		assertTrue(wtPath.exists());
	}

	/** With --force a locked worktree can be removed. */
	@Test
	public void testRemoveLockedWithForce() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-locked-force"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("locked-force-branch") //$NON-NLS-1$
				.setLock(true)
				.call()) {
			// locked
		}

		new WorktreeRemoveCommand(db).setName("wt-locked-force").setForce(true).call(); //$NON-NLS-1$
		assertFalse("working tree must be deleted with --force", wtPath.exists()); //$NON-NLS-1$
	}

	/** Removing an unknown worktree name throws an exception. */
	@Test
	public void testRemoveUnknownName() throws Exception {
		try {
			new WorktreeRemoveCommand(db).setName("nonexistent").call(); //$NON-NLS-1$
			fail("Expected JGitInternalException for unknown name"); //$NON-NLS-1$
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("nonexistent")); //$NON-NLS-1$
		}
	}

	// -----------------------------------------------------------------------
	// WorktreePruneCommand tests
	// -----------------------------------------------------------------------

	/** Prune with no linked worktrees returns an empty list. */
	@Test
	public void testPruneNoWorktrees() throws Exception {
		List<String> pruned = new WorktreePruneCommand(db).call();
		assertTrue(pruned.isEmpty());
	}

	/** A stale admin dir (gitdir points to non-existent path) is pruned. */
	@Test
	public void testPruneStaleAdminDir() throws Exception {
		// Create admin dir manually without a real working tree
		File adminDir = new File(db.getDirectory(), "worktrees/stale-wt"); //$NON-NLS-1$
		adminDir.mkdirs();
		write(new File(adminDir, Constants.HEAD), "ref: refs/heads/master\n"); //$NON-NLS-1$
		write(new File(adminDir, "gitdir"), "/nonexistent/path/.git\n"); //$NON-NLS-1$ //$NON-NLS-2$

		List<String> pruned = new WorktreePruneCommand(db).call();

		assertEquals(1, pruned.size());
		assertEquals("stale-wt", pruned.get(0)); //$NON-NLS-1$
		assertFalse("admin dir must be deleted", adminDir.exists()); //$NON-NLS-1$
	}

	/** Dry-run reports stale worktrees but does not delete them. */
	@Test
	public void testPruneDryRun() throws Exception {
		File adminDir = new File(db.getDirectory(), "worktrees/dry-wt"); //$NON-NLS-1$
		adminDir.mkdirs();
		write(new File(adminDir, Constants.HEAD), "ref: refs/heads/master\n"); //$NON-NLS-1$
		write(new File(adminDir, "gitdir"), "/nonexistent/path/.git\n"); //$NON-NLS-1$ //$NON-NLS-2$

		List<String> pruned = new WorktreePruneCommand(db).setDryRun(true).call();

		assertEquals(1, pruned.size());
		assertTrue("admin dir must still exist after dry-run", adminDir.exists()); //$NON-NLS-1$
	}

	/** A locked stale admin dir is NOT pruned. */
	@Test
	public void testPruneSkipsLocked() throws Exception {
		File adminDir = new File(db.getDirectory(), "worktrees/locked-stale"); //$NON-NLS-1$
		adminDir.mkdirs();
		write(new File(adminDir, Constants.HEAD), "ref: refs/heads/master\n"); //$NON-NLS-1$
		write(new File(adminDir, "gitdir"), "/nonexistent/path/.git\n"); //$NON-NLS-1$ //$NON-NLS-2$
		new File(adminDir, "locked").createNewFile(); //$NON-NLS-1$

		List<String> pruned = new WorktreePruneCommand(db).call();

		assertTrue("locked stale worktree must not be pruned", pruned.isEmpty()); //$NON-NLS-1$
		assertTrue("locked admin dir must still exist", adminDir.exists()); //$NON-NLS-1$
	}

	/** A healthy worktree with a valid gitdir is not pruned. */
	@Test
	public void testPruneKeepsHealthyWorktree() throws Exception {
		File wtPath = new File(db.getWorkTree().getParentFile(), "wt-healthy"); //$NON-NLS-1$
		try (Repository wt = new WorktreeAddCommand(db)
				.setPath(wtPath)
				.setNewBranch("healthy-branch") //$NON-NLS-1$
				.call()) {
			// created
		}

		List<String> pruned = new WorktreePruneCommand(db).call();
		assertTrue("healthy worktree must not be pruned", pruned.isEmpty()); //$NON-NLS-1$
		assertTrue("working tree must still exist", wtPath.exists()); //$NON-NLS-1$
	}
}
