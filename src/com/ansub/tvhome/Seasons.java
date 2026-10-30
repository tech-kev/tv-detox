package com.ansub.tvhome;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// Seasonal accents for the Cinema layout: a greeting, an emoji by it, a colour for the light behind the page,
// a little animation and a countdown, worked out from the date alone (moving feasts included), so it needs no
// connection. Birthdays come from the TV (--es birthdays "Name=MM-DD,..."), never from the code.
final class Seasons {
    static final int SNOW = 1, CONFETTI = 2, HEARTS = 3, EGGS = 4, BATS = 5, DIAMONDS = 6, FIREWORKS = 7,
            SPARKLE = 8, RAIN = 9, LIGHTNING = 10;

    static final class Accent {
        String greeting;    // English key for Strings.t (may hold one %s for a name), or null for the time of day's
        String name = "";
        String emoji = "";
        int glow;           // colour for the light behind the page, 0 for none
        int[] effects = {};
        String note;        // countdown, already in the TV's language, or null
        String[] jokes;     // the season's own jokes, or null
    }

    private Seasons() {}

    // German only: JokeAPI has no German Halloween jokes and a single Christmas one.
    private static final String[] HALLOWEEN_JOKES = {
        "Was ist das Lieblingsessen von Vampiren?\nBlutwurst.",
        "Warum können Skelette so schlecht lügen?\nMan sieht sofort durch sie hindurch.",
        "Was sagt ein Gespenst zum anderen?\nGlaubst du eigentlich an Menschen?",
        "Warum hat der Vampir keine Freunde?\nWeil er so ein Blutsauger ist.",
        "Was macht eine Mumie am Wochenende?\nSie wickelt alles in Ruhe ab.",
        "Was sagt ein Gespenst, wenn es sich gestoßen hat?\nBuuuh-huuu.",
        "Wie nennt man einen Kürbis, der Gewichte stemmt?\nEinen Muskat-Kürbis.",
    };
    private static final String[] CHRISTMAS_JOKES = {
        "Was ist rot-weiß und kommt nicht vom Fleck?\nEin Weihnachtsmann im Stau.",
        "Wie nennt man einen Schneemann im Juli?\nPfütze.",
        "Was sagt der Tannenbaum zum Christbaumschmuck?\nHör endlich auf, bei mir rumzuhängen!",
        "Was sagt man zu einem Weihnachtsbaum mit Prüfungsangst?\nKopf hoch, du wirst schon nicht durchnadeln.",
        "Warum singen Engel so gut?\nSie haben himmlische Stimmen.",
        "Was machen Rentiere, wenn ihnen langweilig ist?\nSie geweihen sich gegenseitig ein.",
    };

    static Accent today(Calendar now, String birthdays, boolean german) {
        Accent a = new Accent();
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH);
        int md = m * 100 + d, hour = now.get(Calendar.HOUR_OF_DAY);
        Calendar easter = easter(y);
        int fourthAdvent = fourthAdvent(y); // day in December
        List<String> notes = new ArrayList<>();

        if (md == 101) {
            set(a, "Happy New Year!", "🥂", 0xFFFFC94D, hour < 1 ? new int[] {FIREWORKS, CONFETTI} : new int[] {CONFETTI});
        } else if (md == 1231) {
            set(a, "Happy New Year's Eve!", "🎆", 0xFFFFC94D, new int[] {SPARKLE});
            if (hour == 23) {
                int left = 60 - now.get(Calendar.MINUTE);
                notes.add(left <= 1 ? Strings.t("Almost there!") : String.format(Strings.t("%d minutes to %d"), left, y + 1));
            }
        } else if (md >= 1224 && md <= 1226) {
            set(a, "Merry Christmas", "🎄", 0xFFE0453A, new int[] {SNOW, SPARKLE});
            a.jokes = CHRISTMAS_JOKES;
        } else if (md == 1206) {
            set(a, "Happy St. Nicholas' Day", "🎅", 0xFFE0453A, new int[] {SNOW});
            a.jokes = CHRISTMAS_JOKES;
        } else if (inAdvent(m, d, fourthAdvent)) {
            int lit = advents(d, m, fourthAdvent);
            StringBuilder candles = new StringBuilder();
            for (int i = 0; i < lit; i++) candles.append("🕯️");
            set(a, null, candles.toString(), 0xFFD9542F, new int[] {SNOW});
            if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && lit > 0) {
                a.greeting = "Happy Advent Sunday %s";
                a.name = String.valueOf(lit);
            }
            a.jokes = CHRISTMAS_JOKES;
        } else if (between(now, add(easter, -52), add(easter, -47))) {
            set(a, "Narri Narro!", "🎭", 0xFFE040C8, new int[] {CONFETTI});
        } else if (md == 214) {
            set(a, "Happy Valentine's Day", "❤️", 0xFFFF6B8A, new int[] {HEARTS});
        } else if (between(now, add(easter, -3), add(easter, 1))) {
            set(a, "Happy Easter", "🐣", 0xFF8FD16A, new int[] {EGGS});
        } else if (md == 501) {
            set(a, "Happy May Day", "🌳", 0xFF7CCB5A, new int[0]);
        } else if (inOktoberfest(now, y)) {
            set(a, "O'zapft is!", "🥨", 0xFF3D8BFF, new int[] {DIAMONDS});
        } else if (md >= 1024 && md <= 1031) {
            set(a, md == 1031 ? "Happy Halloween" : null, "🎃", 0xFFFF7A1A, new int[] {BATS});
            a.jokes = HALLOWEEN_JOKES;
        } else if (md == 1111) {
            set(a, "Happy St. Martin's Day", "🏮", 0xFFFFB24D, new int[0]);
        }

        // Birthdays win the greeting; the season keeps going around them.
        String[] today = birthdayOn(birthdays, m, d);
        if (today != null) {
            a.greeting = "Happy birthday, %s!";
            a.name = today[0];
            a.emoji = "🎂" + (a.emoji.isEmpty() ? "" : " " + a.emoji);
            a.glow = 0xFFFF7AB6;
            int[] fx = new int[a.effects.length + 1];
            fx[0] = CONFETTI;
            System.arraycopy(a.effects, 0, fx, 1, a.effects.length);
            a.effects = fx;
        }
        for (int ahead = 1; ahead <= 3; ahead++) {
            String[] soon = birthday(birthdays, at(now, ahead));
            if (soon == null) continue;
            notes.add(0, ahead == 1 ? String.format(Strings.t("Tomorrow is %s's birthday"), soon[0])
                    : String.format(Strings.t("In %d days it's %s's birthday"), ahead, soon[0]));
            break;
        }

        // What's coming: the day before, or the days to Christmas Eve in December.
        if (md == 1030) notes.add(Strings.t("Tomorrow is Halloween"));
        else if (md == 1205) notes.add(Strings.t("Tomorrow is St. Nicholas' Day"));
        else if (md == 1230) notes.add(Strings.t("Tomorrow is New Year's Eve"));
        else if (md == 1223) notes.add(Strings.t("Tomorrow is Christmas Eve"));
        else if (m == 12 && d < 23) notes.add(String.format(Strings.t("%d days until Christmas Eve"), 24 - d));

        if (!notes.isEmpty()) a.note = android.text.TextUtils.join(" · ", notes.subList(0, Math.min(2, notes.size())));
        if (!german) a.jokes = null;
        return a;
    }

    private static void set(Accent a, String greeting, String emoji, int glow, int[] effects) {
        a.greeting = greeting;
        a.emoji = emoji;
        a.glow = glow;
        a.effects = effects;
    }

    // "Anna=03-14,Ben=11-02": the name whose day is m-d, or null.
    private static String[] birthdayOn(String list, int m, int d) {
        if (list == null) return null;
        for (String entry : list.split(",")) {
            String[] kv = entry.trim().split("=");
            if (kv.length != 2) continue;
            String[] md = kv[1].trim().split("-");
            try {
                if (md.length == 2 && Integer.parseInt(md[0]) == m && Integer.parseInt(md[1]) == d) return new String[] {kv[0].trim()};
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String[] birthday(String list, Calendar day) {
        return birthdayOn(list, day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }

    private static Calendar at(Calendar now, int days) {
        Calendar c = (Calendar) now.clone();
        c.add(Calendar.DAY_OF_MONTH, days);
        return c;
    }

    private static Calendar add(Calendar c, int days) {
        return at(c, days);
    }

    // Day by day, ignoring the time.
    private static boolean between(Calendar now, Calendar from, Calendar to) {
        int n = key(now);
        return n >= key(from) && n <= key(to);
    }

    private static int key(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    // Easter Sunday (Gregorian, the anonymous algorithm).
    static Calendar easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, month - 1, day);
        return cal;
    }

    // The fourth Sunday of Advent: the last Sunday before Christmas Day, 18 to 24 December.
    private static int fourthAdvent(int y) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, Calendar.DECEMBER, 24);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) c.add(Calendar.DAY_OF_MONTH, -1);
        return c.get(Calendar.DAY_OF_MONTH);
    }

    // From the first Sunday of Advent (three weeks before the fourth) to 23 December.
    private static boolean inAdvent(int m, int d, int fourth) {
        int first = fourth - 21; // may fall in November (then <= 0)
        if (m == 12) return d <= 23 && d >= first;
        return m == 11 && first <= 0 && d >= 30 + first;
    }

    // How many Advent candles burn on this day.
    private static int advents(int d, int m, int fourth) {
        int day = m == 12 ? d : d - 30; // November days as negative December days
        int lit = 0;
        for (int s = fourth - 21; s <= fourth; s += 7) if (day >= s) lit++;
        return lit;
    }

    // Munich's Oktoberfest: ends on the first Sunday of October (or 3 October if that's later) and starts
    // on the Saturday 15 days before that Sunday.
    private static boolean inOktoberfest(Calendar now, int y) {
        Calendar end = Calendar.getInstance();
        end.clear();
        end.set(y, Calendar.OCTOBER, 1);
        while (end.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) end.add(Calendar.DAY_OF_MONTH, 1);
        Calendar start = add(end, -15);
        if (end.get(Calendar.DAY_OF_MONTH) < 3) end.set(Calendar.DAY_OF_MONTH, 3);
        return between(now, start, end);
    }
}
