import com.fitlog.app.domain.*;
import java.time.LocalDate;
import java.util.*;
import kotlin.Pair;

public class ScheduleCatchUpCheck {
    static int count;
    static void check(boolean value, String message) { count++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        ScheduleCatchUp rule = ScheduleCatchUp.INSTANCE;
        long monday = LocalDate.of(2026, 10, 19).toEpochDay();
        List<String> week = Arrays.asList("push", "pull", "legs", "rest", "push", "pull", "rest");
        List<String> cycle = Arrays.asList("push", "pull", "legs", "rest");
        Set<String> available = new HashSet<>(Arrays.asList("push", "pull", "legs"));
        Set<Pair<Long, String>> none = Collections.emptySet();
        check(rule.pending("cycle", week, cycle, monday, monday - 1, monday, available, none) == null, "No prompt on first day");
        MissedTraining missed = rule.pending("cycle", week, cycle, monday, monday - 1, monday + 1, available, none);
        check(missed.getDay() == monday && missed.getKey().equals("push"), "Previous unstarted training");
        check(rule.pending("cycle", week, cycle, monday, monday, monday + 1, available, none) == null, "Started day is handled even before record saved");
        Set<Pair<Long, String>> completed = new HashSet<>(Arrays.asList(new Pair<>(monday, "push")));
        check(rule.pending("cycle", week, cycle, monday, monday - 1, monday + 1, available, completed) == null, "Existing matching record excludes prompt");
        check(rule.pending("cycle", week, cycle, monday, monday - 1, monday + 3, available, none) == null, "Scheduled rest never prompts");
        check(rule.pending("week", week, cycle, monday, monday - 1, monday + 3, available, none) == null, "Weekly rest never prompts");
        check(rule.pending("cycle", week, cycle, monday, monday - 1, monday + 4, available, none).getDay() == monday, "Several absences keep first unfinished arrangement");
        check(rule.pending("cycle", week, cycle, monday, monday - 1, monday + 2, Collections.singleton("pull"), none) == null, "Removed today plan does not prompt");
        check(rule.pending("cycle", week, cycle, monday, monday - 1, monday + 2, new HashSet<>(Arrays.asList("pull", "legs")), none).getKey().equals("pull"), "Removed previous plan is skipped");
        long shifted = rule.shiftedStart("cycle", monday, monday, monday + 2);
        check(shifted == monday + 2, "Shift entire day cycle by missed days");
        check(TrainingSchedule.INSTANCE.resolve("cycle", week, cycle, shifted, monday + 2, 3).equals("push"), "Resumed day keeps missed workout");
        check(TrainingSchedule.INSTANCE.resolve("cycle", week, cycle, shifted, monday + 3, 4).equals("pull"), "Following workout shifts too");
        check(TrainingSchedule.INSTANCE.resolve("cycle", week, cycle, shifted, monday + 5, 6).equals("rest"), "Rest shifts with cycle");
        check(rule.shiftedStart("week", monday, monday, monday + 2) == monday, "Weekly schedule never shifts");
        check(TrainingSchedule.INSTANCE.resolve("week", week, cycle, monday, monday + 2, 3).equals("legs"), "Next weekly arrangement remains fixed");
        check(rule.pending("week", week, cycle, monday, monday - 1, monday + 1, available, none).getKey().equals("push"), "Weekly missed workout can replace today");
        String route = rule.route("plan:42", monday, true);
        check(rule.key(route).equals("plan:42"), "Navigation preserves plan IDs");
        check(rule.choice(route).getFirst() == monday && rule.choice(route).getSecond(), "Resume carries date and choice");
        check(!rule.choice(rule.route("pull", monday, false)).getSecond(), "Today choice does not shift");
        check(rule.choice("scheduled|broken") == null && rule.key("active").equals("active"), "Legacy and invalid routes are safe");
        check(rule.pending("cycle", week, cycle, monday, monday + 2, monday + 1, available, none) == null, "Clock moving backwards creates no missed day");
        System.out.println(count + " schedule catch-up checks passed");
    }
}
