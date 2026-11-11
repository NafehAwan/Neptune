package net.tuffclient.legacy.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Counts clicks in the last second (fed by MinecraftClientMixin). */
public final class Cps {
	private static final Deque<Long> LEFT = new ArrayDeque<Long>(), RIGHT = new ArrayDeque<Long>();

	private Cps() {
	}

	public static void click(boolean left) {
		(left ? LEFT : RIGHT).addLast(System.currentTimeMillis());
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.pollFirst();
		return clicks.size();
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}
}
