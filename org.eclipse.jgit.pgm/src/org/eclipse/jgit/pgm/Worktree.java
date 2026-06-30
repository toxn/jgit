/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.WorktreeAddCommand;
import org.eclipse.jgit.api.WorktreeListCommand;
import org.eclipse.jgit.api.WorktreeLockCommand;
import org.eclipse.jgit.api.WorktreeMoveCommand;
import org.eclipse.jgit.api.WorktreePruneCommand;
import org.eclipse.jgit.api.WorktreeRemoveCommand;
import org.eclipse.jgit.api.WorktreeRepairCommand;
import org.eclipse.jgit.api.WorktreeUnlockCommand;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.worktree.WorktreeReference;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Manage multiple working trees.
 *
 * <p>
 * Supported subcommands: {@code add}, {@code list}.
 *
 * <p>
 * Future subcommands (not yet implemented): {@code lock}, {@code move},
 * {@code prune}, {@code remove}, {@code repair}, {@code unlock}.
 *
 * @see <a href=
 *      "https://git-scm.com/docs/git-worktree">Git documentation about
 *      worktrees</a>
 */
@Command(common = false, usage = "usage_Worktree")
class Worktree extends TextBuiltin {

	/** Subcommand: add, list, lock, move, prune, remove, repair, unlock. */
	@Argument(index = 0, metaVar = "metaVar_command")
	private String command;

	/** Additional arguments (path, name, reason, …). */
	@Argument(index = 1, multiValued = true, metaVar = "metaVar_path")
	private List<String> args;

	// --- list options ---

	@Option(name = "--porcelain", usage = "usage_worktreeListPorcelain")
	private boolean porcelain;

	@Option(name = "-z", usage = "usage_worktreeListNul")
	private boolean nulTerminate;

	@Option(name = "--verbose", aliases = {
			"-v" }, usage = "usage_beVerbose")
	private boolean verbose;

	// --- add options ---

	@Option(name = "-b", metaVar = "metaVar_branchName",
			usage = "usage_worktreeAddNewBranch")
	private String newBranch;

	@Option(name = "-B", metaVar = "metaVar_branchName",
			usage = "usage_worktreeAddForceBranch")
	private String forceNewBranch;

	@Option(name = "--detach", usage = "usage_worktreeAddDetach")
	private boolean detach;

	@Option(name = "--no-checkout", usage = "usage_worktreeAddNoCheckout")
	private boolean noCheckout;

	@Option(name = "--force", usage = "usage_worktreeAddForce")
	private boolean force;

	@Option(name = "--lock", usage = "usage_worktreeAddLock")
	private boolean lock;

	@Option(name = "--reason", metaVar = "metaVar_reason",
			usage = "usage_worktreeAddLockReason")
	private String lockReason;

	// --- prune options ---

	@Option(name = "--dry-run", aliases = {
			"-n" }, usage = "usage_dryRun")
	private boolean dryRun;

	@Override
	protected void run() throws Exception {
		if (command == null) {
			// Default: list (same as git worktree with no subcommand)
			runList();
			return;
		}
		try (Git git = new Git(db)) {
			switch (command) {
			case "add": //$NON-NLS-1$
				runAdd();
				break;
			case "list": //$NON-NLS-1$
				runList();
				break;
			case "remove": //$NON-NLS-1$
			case "rm": //$NON-NLS-1$
				runRemove();
				break;
			case "prune": //$NON-NLS-1$
				runPrune();
				break;
			case "lock": //$NON-NLS-1$
				runLock();
				break;
			case "unlock": //$NON-NLS-1$
				runUnlock();
				break;
			case "move": //$NON-NLS-1$
			case "mv": //$NON-NLS-1$
				runMove();
				break;
			case "repair": //$NON-NLS-1$
				runRepair();
				break;
			default:
				throw new JGitInternalException(MessageFormat.format(
						CLIText.get().unknownSubcommand, command));
			}
		}
	}

	private void runAdd() throws Exception {
		if (args == null || args.isEmpty()) {
			throw new JGitInternalException(
					CLIText.get().worktreeSubcommandRequired);
		}
		String pathArg = args.get(0);
		File wtPath = new File(pathArg);
		if (!wtPath.isAbsolute()) {
			// Resolve relative to the working directory
			wtPath = new File(System.getProperty("user.dir"), pathArg); //$NON-NLS-1$
		}

		WorktreeAddCommand addCmd = new WorktreeAddCommand(db).setPath(wtPath);
		if (newBranch != null) {
			addCmd.setNewBranch(newBranch);
		} else if (forceNewBranch != null) {
			addCmd.setNewBranch(forceNewBranch);
			addCmd.setForceNewBranch(true);
		}
		if (detach) {
			addCmd.setDetach(true);
		}
		if (noCheckout) {
			addCmd.setCheckout(false);
		}
		if (force) {
			addCmd.setForce(true);
		}
		if (lock) {
			addCmd.setLock(true);
			addCmd.setLockReason(lockReason);
		}
		// start point: second arg if present
		if (args.size() > 1) {
			addCmd.setStartPoint(args.get(1));
		}

		try (org.eclipse.jgit.lib.Repository wt = addCmd.call()) {
			outw.println(MessageFormat.format(
					CLIText.get().worktreeAdded,
					wt.getWorkTree().getAbsolutePath()));
		}
	}

	private void runRemove() throws Exception {
		if (args == null || args.isEmpty()) {
			throw new JGitInternalException(
					CLIText.get().worktreeSubcommandRequired);
		}
		new WorktreeRemoveCommand(db)
				.setName(args.get(0))
				.setForce(force)
				.call();
	}

	private void runPrune() throws Exception {
		WorktreePruneCommand pruneCmd = new WorktreePruneCommand(db)
				.setDryRun(dryRun);
		List<String> pruned = pruneCmd.call();
		for (String n : pruned) {
			String prefix = dryRun ? "Would remove " : "Removing "; //$NON-NLS-1$ //$NON-NLS-2$
			outw.println(prefix + n);
		}
	}

	private void runLock() throws Exception {
		if (args == null || args.isEmpty()) {
			throw new JGitInternalException(
					CLIText.get().worktreeSubcommandRequired);
		}
		new WorktreeLockCommand(db)
				.setName(args.get(0))
				.setReason(lockReason)
				.call();
	}

	private void runUnlock() throws Exception {
		if (args == null || args.isEmpty()) {
			throw new JGitInternalException(
					CLIText.get().worktreeSubcommandRequired);
		}
		new WorktreeUnlockCommand(db)
				.setName(args.get(0))
				.call();
	}

	private void runMove() throws Exception {
		if (args == null || args.size() < 2) {
			throw new JGitInternalException(
					CLIText.get().worktreeSubcommandRequired);
		}
		File dest = new File(args.get(1));
		if (!dest.isAbsolute()) {
			dest = new File(System.getProperty("user.dir"), args.get(1)); //$NON-NLS-1$
		}
		new WorktreeMoveCommand(db)
				.setName(args.get(0))
				.setNewPath(dest)
				.setForce(force)
				.call();
	}

	private void runRepair() throws Exception {
		if (args == null || args.size() < 2 || args.size() % 2 != 0) {
			throw new JGitInternalException(
					CLIText.get().worktreeSubcommandRequired);
		}
		WorktreeRepairCommand repairCmd = new WorktreeRepairCommand(db);
		for (int i = 0; i < args.size(); i += 2) {
			String wName = args.get(i);
			File newPath = new File(args.get(i + 1));
			if (!newPath.isAbsolute()) {
				newPath = new File(System.getProperty("user.dir"), args.get(i + 1)); //$NON-NLS-1$
			}
			repairCmd.addPath(wName, newPath);
		}
		List<String> repaired = repairCmd.call();
		for (String n : repaired) {
			outw.println("Repaired worktree " + n); //$NON-NLS-1$
		}
	}


	private void runList() throws Exception {
		WorktreeListCommand cmd = new WorktreeListCommand(db);
		List<WorktreeReference> worktrees = cmd.call();

		if (porcelain) {
			printPorcelain(worktrees);
		} else {
			printDefault(worktrees);
		}
	}

	/**
	 * Default human-readable format (mirrors git worktree list output):
	 *
	 * <pre>
	 * /path/to/worktree  abcdef12 [branch-name]
	 * /path/to/other     deadbeef (detached HEAD)
	 * </pre>
	 */
	private void printDefault(List<WorktreeReference> worktrees)
			throws IOException {
		// Compute maximum path width for alignment
		int maxPath = 0;
		for (WorktreeReference wt : worktrees) {
			String path = worktreePath(wt);
			if (path.length() > maxPath) {
				maxPath = path.length();
			}
		}

		for (WorktreeReference wt : worktrees) {
			String path = worktreePath(wt);
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%-" + maxPath + "s", path)); //$NON-NLS-1$
			sb.append(' ');

			if (wt.isBare()) {
				sb.append("(bare)"); //$NON-NLS-1$
			} else {
				ObjectId head = wt.getHead();
				if (head != null) {
					sb.append(head.abbreviate(7).name());
				} else {
					sb.append("0000000"); //$NON-NLS-1$
				}
				sb.append(' ');
				if (wt.isDetached()) {
					sb.append("(detached HEAD)"); //$NON-NLS-1$
				} else if (wt.getBranch() != null) {
					sb.append('[');
					sb.append(shortBranch(wt.getBranch()));
					sb.append(']');
				}
			}

			if (wt.isLocked()) {
				sb.append(" locked"); //$NON-NLS-1$
			}
			if (wt.isPrunable()) {
				sb.append(" prunable"); //$NON-NLS-1$
			}

			outw.println(sb.toString());
		}
	}

	/**
	 * Porcelain format (mirrors git worktree list --porcelain):
	 *
	 * <pre>
	 * worktree /absolute/path
	 * HEAD abc123...
	 * branch refs/heads/main
	 *
	 * worktree /other/path
	 * HEAD def456...
	 * detached
	 *
	 * </pre>
	 */
	private void printPorcelain(List<WorktreeReference> worktrees)
			throws IOException {
		String separator = nulTerminate ? "\0" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		for (WorktreeReference wt : worktrees) {
			outw.print("worktree " + worktreePath(wt) + separator); //$NON-NLS-1$
			ObjectId head = wt.getHead();
			if (head != null) {
				outw.print("HEAD " + head.name() + separator); //$NON-NLS-1$
			}
			if (wt.isBare()) {
				outw.print("bare" + separator); //$NON-NLS-1$
			} else if (wt.isDetached()) {
				outw.print("detached" + separator); //$NON-NLS-1$
			} else if (wt.getBranch() != null) {
				outw.print("branch " + wt.getBranch() + separator); //$NON-NLS-1$
			}
			if (wt.isLocked()) {
				String reason = wt.getLockReason();
				if (reason != null && !reason.isEmpty()) {
					outw.print("locked " + reason + separator); //$NON-NLS-1$
				} else {
					outw.print("locked" + separator); //$NON-NLS-1$
				}
			}
			if (wt.isPrunable()) {
				String reason = wt.getPrunableReason();
				if (reason != null && !reason.isEmpty()) {
					outw.print("prunable " + reason + separator); //$NON-NLS-1$
				} else {
					outw.print("prunable" + separator); //$NON-NLS-1$
				}
			}
			outw.print(separator); // blank line between entries
		}
	}

	@Override
	public void printUsage(String message, CmdLineParser clp)
			throws IOException {
		errw.println(message);
		errw.println("jgit worktree list [--porcelain [-z]] [--help (-h)]"); //$NON-NLS-1$
		errw.println();
		clp.printUsage(errw, getResourceBundle());
		errw.println();
		errw.flush();
	}

	private static String worktreePath(WorktreeReference wt) {
		return wt.getWorktreePath() != null
				? wt.getWorktreePath().getAbsolutePath()
				: "(no working tree)"; //$NON-NLS-1$
	}

	private static String shortBranch(String fullRef) {
		if (fullRef.startsWith("refs/heads/")) { //$NON-NLS-1$
			return fullRef.substring("refs/heads/".length()); //$NON-NLS-1$
		}
		return fullRef;
	}
}
