/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;


/**
 * The supervisor's settings kept from one run to the next: preferences, colors, window
 * placement and folded panels, in the user's preferences store. Every change is written at
 * once; each part of the supervisor reads its own settings when it starts.
 */
public final class SupervisorSettings {

	/** Key of the visual theme: the name of a {@link SupervisorTheme}, {@code LIGHT} when unset. */
	public static final String THEME = "theme";
	/**
	 * Key of the boolean telling whether the component diagram recalculates its layout when components are
	 * hidden; {@code false} when unset.
	 */
	public static final String AUTO_LAYOUT_ON_HIDE = "diagram.autoLayoutOnHide";
	/**
	 * Key of the boolean telling whether a sequence diagram is drawn live, as messages arrive, by default;
	 * {@code true} when unset.
	 */
	public static final String LIVE_DRAWING_DEFAULT = "sequence.liveDrawingDefault";
	/**
	 * Key of the default timeline style of recorded sequences: the name of a {@code TimelineStyle}, {@code
	 * SWITCH} when unset.
	 */
	public static final String TIMELINE_STYLE = "sequence.timelineStyle";
	/**
	 * Key of the boolean telling whether data flows are switched on when the supervisor connects to a
	 * container; {@code false} when unset.
	 */
	public static final String FLOWS_ON_CONNECT = "flows.onConnect";
	/** Key of the absolute path of the folder snapshots are saved in; unset means the working directory. */
	public static final String SNAPSHOT_DIRECTORY = "snapshot.directory";
	/**
	 * Key of the absolute path of the folder saved diagram views are kept in; unset means {@code
	 * ~/.covalent/supervisor/views}.
	 */
	public static final String VIEWS_DIRECTORY = "views.directory";
	/** Key of whether a new recording keeps a live log of its messages; {@code false} when unset. */
	public static final String SEQUENCE_LIVE_LOG = "sequences.liveLog";
	/** Key of the format of the sequence logs, a {@code SequenceLog.Format} name; text when unset. */
	public static final String SEQUENCE_LOG_FORMAT = "sequences.logFormat";
	/**
	 * Key of the absolute path of the folder live sequence logs are written in; unset means {@code
	 * ~/.covalent/supervisor/sequences}.
	 */
	public static final String SEQUENCE_LOG_DIRECTORY = "sequences.logDirectory";
	/** Key of the horizontal position of the supervisor's window, in screen coordinates, as a double. */
	public static final String WINDOW_X = "window.x";
	/** Key of the vertical position of the supervisor's window, in screen coordinates, as a double. */
	public static final String WINDOW_Y = "window.y";
	/**
	 * Key of the width of the supervisor's window, as a double; a placement is restored only when wider
	 * than 200.
	 */
	public static final String WINDOW_WIDTH = "window.width";
	/**
	 * Key of the height of the supervisor's window, as a double; a placement is restored only when taller
	 * than 150.
	 */
	public static final String WINDOW_HEIGHT = "window.height";
	/**
	 * Key of the boolean telling whether the supervisor's window was left maximized; {@code false} when
	 * unset.
	 */
	public static final String WINDOW_MAXIMIZED = "window.maximized";
	/** Key of the horizontal position of the help window, in screen coordinates, as a double. */
	public static final String HELP_WINDOW_X = "help.window.x";
	/** Key of the vertical position of the help window, in screen coordinates, as a double. */
	public static final String HELP_WINDOW_Y = "help.window.y";
	/** Key of the width of the help window, as a double. */
	public static final String HELP_WINDOW_WIDTH = "help.window.width";
	/** Key of the height of the help window, as a double. */
	public static final String HELP_WINDOW_HEIGHT = "help.window.height";
	/**
	 * Key of the boolean telling whether Covalent's own components are excluded from the diagrams; {@code
	 * true} when unset.
	 */
	public static final String EXCLUDE_INTERNAL_COMPONENTS = "exclusions.excludeInternal";
	/**
	 * Key of the list of class names of the components the user excluded, on top of the built-in
	 * exclusions.
	 */
	public static final String EXCLUDED_COMPONENTS = "exclusions.components";
	/** The stereotype rules the user added: one {@code ending=stereotype} text each. */
	public static final String STEREOTYPE_RULES = "stereotypes.rules";

	/** The name endings of the built-in stereotype rules the user removed. */
	public static final String STEREOTYPE_REMOVED_BUILT_INS = "stereotypes.removedBuiltIns";
	/**
	 * Key of the boolean telling whether thread tracking starts when the supervisor connects to a
	 * container; {@code false} when unset.
	 */
	public static final String THREADS_TRACKING_ON_CONNECT = "threads.trackingOnConnect";
	/**
	 * Key of the duration, in seconds, after which a running task counts as long; {@code 10} when unset,
	 * at least 1 when applied.
	 */
	public static final String THREADS_LONG_TASK_SECONDS = "threads.longTaskSeconds";
	/** The supervisor's language: {@code system}, {@code en} or {@code fr}, applied at the next start. */
	public static final String LANGUAGE = "language";

	private static final Preferences STORE = Preferences.userRoot().node("org/opales/covalent/supervisor");

	private SupervisorSettings() {
	}

	/**
	 * Returns a stored boolean.
	 *
	 * @param key the setting's key
	 * @param defaultValue the value returned when none is stored
	 * @return the stored value, or the default
	 */
	public static boolean getBoolean(final String key, final boolean defaultValue) {
		return STORE.getBoolean(key, defaultValue);
	}

	/**
	 * Stores a boolean and writes it out at once.
	 *
	 * @param key the setting's key
	 * @param value the value to store
	 */
	public static void putBoolean(final String key, final boolean value) {
		STORE.putBoolean(key, value);
		flush();
	}

	/**
	 * Returns a stored double.
	 *
	 * @param key the setting's key
	 * @param defaultValue the value returned when none is stored
	 * @return the stored value, or the default
	 */
	public static double getDouble(final String key, final double defaultValue) {
		return STORE.getDouble(key, defaultValue);
	}

	/**
	 * Stores a double and writes it out at once.
	 *
	 * @param key the setting's key
	 * @param value the value to store
	 */
	public static void putDouble(final String key, final double value) {
		STORE.putDouble(key, value);
		flush();
	}

	/**
	 * Returns a stored text.
	 *
	 * @param key the setting's key
	 * @return the stored text, {@code null} when none
	 */
	public static String getString(final String key) {
		return STORE.get(key, null);
	}

	/**
	 * Stores a text and writes it out at once.
	 *
	 * @param key the setting's key
	 * @param value the text to store
	 */
	public static void putString(final String key, final String value) {
		STORE.put(key, value);
		flush();
	}

	/**
	 * Returns a stored list of texts, kept in its own node, one entry per item, so that its
	 * length is not bounded by the size of a single value.
	 *
	 * @param key the setting's key
	 * @return the stored texts in order, {@code null} when none was ever stored
	 */
	public static List<String> getStringList(final String key) {
		try {
			if (!STORE.nodeExists(key)) {
				return null;
			}
			Preferences node = STORE.node(key);
			List<String> values = new ArrayList<>();
			for (int index = 0;; index++) {
				String value = node.get(Integer.toString(index), null);
				if (value == null) {
					return values;
				}
				values.add(value);
			}
		} catch (BackingStoreException | IllegalStateException e) {
			return null;
		}
	}

	/**
	 * Stores a list of texts in its own node, one entry per item, replacing the list stored before.
	 *
	 * @param key the setting's key
	 * @param values the texts to store, in order
	 */
	public static void putStringList(final String key, final List<String> values) {
		Preferences node = STORE.node(key);
		try {
			node.clear();
		} catch (BackingStoreException e) {
			// Overwritten below as far as the store allows.
		}
		for (int index = 0; index < values.size(); index++) {
			node.put(Integer.toString(index), values.get(index));
		}
		flush();
	}

	/**
	 * Returns a stored enum constant.
	 *
	 * @param <E> the enum type
	 * @param key the setting's key
	 * @param defaultValue the value returned when none is stored or its name is unknown; also gives the enum type
	 * @return the stored constant, or the default
	 */
	public static <E extends Enum<E>> E getEnum(final String key, final E defaultValue) {
		String name = STORE.get(key, null);
		if (name == null) {
			return defaultValue;
		}
		try {
			return Enum.valueOf(defaultValue.getDeclaringClass(), name);
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	/**
	 * Stores an enum constant by name and writes it out at once.
	 *
	 * @param key the setting's key
	 * @param value the constant to store
	 */
	public static void putEnum(final String key, final Enum<?> value) {
		putString(key, value.name());
	}

	/**
	 * Returns the color the user chose for an element in a theme.
	 *
	 * @param theme the theme the color belongs to
	 * @param element the name of the colored element
	 * @return the color as {@code #rrggbbaa}, {@code null} when none
	 */
	public static String getColor(final SupervisorTheme theme, final String element) {
		return STORE.get(colorKey(theme, element), null);
	}

	/**
	 * Stores the color chosen for an element in a theme and writes it out at once.
	 *
	 * @param theme the theme the color belongs to
	 * @param element the name of the colored element
	 * @param color the color to store, as {@code #rrggbbaa}
	 */
	public static void putColor(final SupervisorTheme theme, final String element, final String color) {
		STORE.put(colorKey(theme, element), color);
		flush();
	}

	/**
	 * Tells whether a panel was left folded.
	 *
	 * @param panel the panel's name
	 * @return {@code true} when it was left folded, {@code false} when unset
	 */
	public static boolean isPanelCollapsed(final String panel) {
		return STORE.getBoolean("panel." + panel + ".collapsed", false);
	}

	/**
	 * Stores whether a panel is folded and writes it out at once.
	 *
	 * @param panel the panel's name
	 * @param collapsed {@code true} when it is folded
	 */
	public static void setPanelCollapsed(final String panel, final boolean collapsed) {
		putBoolean("panel." + panel + ".collapsed", collapsed);
	}

	/**
	 * Forgets the colors chosen for some elements in a theme and writes it out at once; the
	 * other theme's colors are kept.
	 *
	 * @param theme the theme the colors belong to
	 * @param elements the names of the colored elements
	 */
	public static void removeColors(final SupervisorTheme theme, final Collection<String> elements) {
		for (String element : elements) {
			STORE.remove(colorKey(theme, element));
		}
		flush();
	}

	private static String colorKey(final SupervisorTheme theme, final String element) {
		return "color." + theme.name() + "." + element;
	}

	private static void flush() {
		try {
			STORE.flush();
		} catch (BackingStoreException e) {
			// Kept in memory for this run; nothing else to do.
		}
	}
}
