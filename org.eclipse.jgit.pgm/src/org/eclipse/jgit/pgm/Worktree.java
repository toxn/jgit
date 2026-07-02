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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.WorktreeListCommand;
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
 * Supported subcommands: {@code list}.
 *
 * <p>
 * Future subcommands (not yet implemented): {@code add}, {@code lock},
 * {@code move}, {@code prune}, {@code remove}, {@code repair},
 * {@code unlock}.
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

	/** Additional arguments (path, name, reason …). */
	@Argument(index = 1, multiValued = true, metaVar = "metaVar_path")
	private List<String> args;

	@Option(name = "--porcelain", usage = "usage_worktreeListPorcelain")
	private boolean porcelain;

	@Option(name = "-z", usage = "usage_worktreeListNul")
	private boolean nulTerminate;

	@Option(name = "--verbose", aliases = {
			"-v" }, usage = "usage_beVerbose")
	private boolean verbose;

	@Override
	protected void run() throws Exception {
		if (command == null) {
			// Default: list (same as git worktree with no subcommand)
			runList();
			return;
		}
		try (Git git = new Git(db)) {
			switch (command) {
			case "list": //$NON-NLS-1$
				runList();
				break;
			default:
				throw new JGitInternalException(MessageFormat.format(
						CLIText.get().unknownSubcommand, command));
			}
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
