import com.fitlog.app.domain.*;
import java.util.Arrays;

/** Verify handoff eligibility against real engine transitions, not fabricated UI states. */
public final class ExerciseHandoffCheck {
    private static int checks;
    private static void check(String label, boolean value) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) {
        WorkoutEngine e = WorkoutEngine.INSTANCE;
        ExerciseHandoffPolicy p = ExerciseHandoffPolicy.INSTANCE;
        WorkoutSession start = e.start("训练", Arrays.asList(new ExerciseSpec("卧推", 2, 10, 40, 5),
            new ExerciseSpec("划船", 1, 10, 30, 0), new ExerciseSpec("深蹲", 1, 10, 50, 0)), 0L, "handoff");
        WorkoutSession first = e.complete(start, 1000L);
        check("first group is not a new exercise", !p.shouldShow(start, first));
        check("resting double tap rejected", !p.shouldShow(first, e.complete(first, 1100L)));
        WorkoutSession ready = e.skipRest(first, 2000L);
        check("skip rest stays on the same exercise", !p.shouldShow(first, ready));
        WorkoutSession second = e.complete(ready, 3000L);
        check("last set introduces next exercise", p.shouldShow(ready, second));
        check("introduction does not alter rest", second.restSeconds(3000L) == 5);
        check("current name is the next exercise", second.getCurrent().getName().equals("划船"));
        check("restored screen does not replay", !p.shouldShow(second, second));
        check("undo has no forward introduction", !p.shouldShow(second, ready));
        check("parameter-only advance has no introduction", !p.shouldShow(first, e.resizeCurrentSets(first, 1, 4000L)));
        check("weight edit does not introduce", !p.shouldShow(second, e.adjust(second, 2.5, 0)));
        WorkoutSession secondReady = e.skipRest(second, 4000L);
        check("rest of next exercise ends without replay", !p.shouldShow(second, secondReady));
        WorkoutSession third = e.complete(secondReady, 5000L);
        check("zero-rest handoff works", p.shouldShow(secondReady, third));
        WorkoutSession ended = e.complete(third, 6000L);
        check("final exercise does not introduce a nonexistent action", !p.shouldShow(third, ended));
        check("redo completion introduces again", p.shouldShow(ready, e.complete(ready, 7000L)));
        WorkoutSession other = e.start("另一训练", start.getExercises(), 0L, "different");
        WorkoutSession otherReady = e.skipRest(e.complete(other, 1000L), 2000L);
        check("different sessions never share handoffs", !p.shouldShow(ready, e.complete(otherReady, 3000L)));
        ExerciseSpec cardio = new ExerciseSpec("游泳", 1, 10, 0, 0, false, "全身", "", "", true);
        WorkoutSession swim = e.start("混合", Arrays.asList(cardio, new ExerciseSpec("划船")), 0L, "cardio");
        WorkoutSession afterSwim = e.complete(swim, 30000L, 30, 25.0, 10);
        check("cardio segment completion also introduces", p.shouldShow(swim, afterSwim));
        System.out.println("Exercise handoff: " + checks + " engine-based checks passed");
    }
}
