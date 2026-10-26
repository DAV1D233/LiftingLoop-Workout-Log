import com.fitlog.app.domain.*;
import java.util.*;

public class CardioEngineCheck {
    static int count;
    static void check(boolean ok, String message) { count++; if (!ok) throw new AssertionError(message); }
    static ExerciseSpec cardio(String mode, int rounds, int seconds, int rest, double meters, String training) {
        return new ExerciseSpec("跑步", rounds, 20, 0, rest, false, "", "", "", true,
            Collections.emptyList(), new CardioTarget(mode, seconds, meters, 0, "", training));
    }
    static WorkoutSession start(ExerciseSpec... specs) {
        return WorkoutEngine.INSTANCE.start("有氧测试", Arrays.asList(specs), 0);
    }
    public static void main(String[] args) {
        CardioEngine engine = CardioEngine.INSTANCE;
        WorkoutSession free = start(cardio("自由", 1, 0, 0, 0, "continuous"));
        check(engine.tick(free, 100000).getCompleted().isEmpty(), "Continuous exercise never completes itself");
        WorkoutSession paused = engine.pause(free, 5500);
        check(paused.segmentSeconds(99000) == 5 && paused.durationSeconds(99000) == 5, "Pause freezes active and overall clocks");
        check(engine.tick(paused, 99000).equals(paused), "Pause prevents automatic phase transitions");
        check(engine.pause(paused, 12000).equals(paused), "Repeated pause is idempotent");
        WorkoutSession resumed = engine.resume(paused, 15500);
        check(resumed.segmentSeconds(20000) == 10 && resumed.durationSeconds(20000) == 10, "Resume excludes exact paused milliseconds");
        check(engine.resume(resumed, 21000).equals(resumed), "Repeated resume is idempotent");
        WorkoutSession frozen = engine.pause(resumed, 20500);
        WorkoutSession review = engine.finishCurrent(frozen, 35000, false);
        check(review.getPendingCardioReview() == 0 && review.getCompleted().size() == 1, "Continuous finish opens one persisted review");
        check(review.getCompleted().get(0).getDurationSeconds() == 10, "Finishing while paused excludes waiting time");
        WorkoutSession confirmed = engine.confirm(review, 10, 5000, 0, 40000);
        check(confirmed.getPendingCardioReview() == null && confirmed.getCardioDistance() == 5000, "Confirmed actual distance is recorded");
        check(confirmed.durationSeconds(99999) == 10, "Review waiting never inflates ended workout duration");
        check(engine.confirm(confirmed, 10, 5000, 0, 50000).equals(confirmed), "Second confirmation does not duplicate data");
        WorkoutSession goal = start(cardio("距离", 1, 0, 0, 5000, "continuous"));
        WorkoutSession goalReview = engine.finishCurrent(goal, 60000, false);
        check(goalReview.getCompleted().get(0).getDistanceMeters() == 5000, "Goal distance prepopulates actual-distance review");
        check(engine.confirm(goalReview, 60, 3200, 0, 70000).getCardioDistance() == 3200, "Prefilled goal can be corrected to actual distance");
        check(!engine.goalsMet(engine.confirm(goalReview, 60, 3200, 0, 70000)), "A corrected short distance does not claim goal achievement");
        check(engine.goalsMet(engine.confirm(goalReview, 60, 5000, 0, 70000)), "Confirmed goal distance counts as achieved");
        WorkoutSession interval = start(cardio("计时", 3, 2, 1, 100, "interval"));
        check(engine.tick(interval, 1999).getCompleted().isEmpty(), "Work does not end before its deadline");
        WorkoutSession rest = engine.tick(interval, 2000);
        check(rest.getCompleted().size() == 1 && rest.restSeconds(2000) == 1, "Timed work automatically enters rest");
        check(rest.segmentSeconds(2500) == 0, "Rest is excluded from current active time");
        WorkoutSession second = engine.tick(rest, 4000);
        check(second.getCompleted().size() == 1 && second.segmentSeconds(4000) == 1, "Automatic rest expiry starts next work at exact boundary");
        WorkoutSession ended = engine.tick(interval, 12000);
        check(ended.getCompleted().size() == 3 && ended.getPendingCardioReview() == 0, "Background catch-up completes all due rounds and waits for review");
        check(ended.getEndedAt() == 8000 && ended.getCardioSeconds() == 6, "Catch-up retains exact work duration and end time");
        check(ended.getCardioDistance() == 300, "Interval distance goal is prefilled for completed rounds");
        check(engine.tick(ended, 40000).equals(ended), "Repeated background ticks cannot create duplicate rounds");
        check(engine.completeRound(ended, 40000).equals(ended), "Review blocks duplicate manual completion");
        WorkoutSession adjusted = engine.confirm(ended, 7, 250, 0, 40000);
        check(adjusted.getCardioSeconds() == 7 && Math.abs(adjusted.getCardioDistance() - 250) < .00001, "One review reconciles totals across all rounds");
        for (CompletedSet log : adjusted.getCompleted()) check(WorkoutLogEditing.INSTANCE.fromCompleted(log).valid(), "Reconciled round remains valid");
        WorkoutSession workPause = engine.pause(interval, 500);
        workPause = engine.resume(workPause, 1500);
        check(engine.tick(workPause, 2999).getCompleted().isEmpty(), "Partial-second work pause shifts deadline precisely");
        WorkoutSession pausedRest = engine.pause(engine.tick(workPause, 3000), 3500);
        check(pausedRest.restSeconds(10000) == 1, "Rest countdown freezes while paused");
        WorkoutSession restResume = engine.resume(pausedRest, 6500);
        check(engine.tick(restResume, 6999).getRestUntil() != null, "Resumed rest retains remaining half second");
        WorkoutSession next = engine.tick(restResume, 7000);
        check(next.getRestUntil() == null && next.segmentSeconds(7000) == 0, "Rest pause does not leak into next work timer");
        WorkoutSession stopped = engine.finishCurrent(rest, 2500, false);
        check(stopped.getCompleted().size() == 1 && stopped.getExercises().get(0).getSets() == 1, "Early finish during rest preserves completed rounds only");
        check(CardioKt.isInterval(stopped.getExercises().get(0)), "Early finish preserves interval identity");
        check(!engine.goalsMet(engine.confirm(stopped, 2, 100, 0, 3000)), "Ending intervals early does not claim all planned rounds achieved");
        ExerciseSpec strength = new ExerciseSpec("卧推", 1, 10, 20, 0);
        WorkoutSession mixed = start(cardio("计时", 2, 2, 1, 0, "interval"), strength);
        WorkoutSession awaiting = engine.tick(mixed, 20000);
        check(awaiting.getIndex() == 1 && awaiting.getPendingCardioReview() == 0 && awaiting.getPausedAt() == 5000,
            "Mixed session waits for cardio confirmation before starting strength");
        WorkoutSession onward = engine.confirm(awaiting, 4, 0, 0, 20000);
        check(onward.getIndex() == 1 && onward.segmentSeconds(20000) == 0 && onward.getPausedAt() == null,
            "Confirm resumes the next exercise with a fresh timer");
        check(onward.durationSeconds(22000) == 7, "Mixed duration excludes review waiting but includes rest");
        WorkoutSession whole = engine.finishCurrent(mixed, 1000, true);
        check(whole.getEndedAt() == 1000 && !whole.getAllComplete(), "Explicit whole-workout finish preserves incomplete status");
        WorkoutSession counted = start(cardio("计次", 2, 0, 0, 0, "interval"));
        check(engine.tick(counted, 100000).getCompleted().isEmpty(), "Count-based rounds require user completion");
        counted = engine.completeRound(counted, 1000);
        counted = engine.completeRound(counted, 2000);
        check(counted.getPendingCardioReview() == 0 && counted.getCompleted().size() == 2, "Count-based rounds use a single final review");
        counted = engine.confirm(counted, 2, 0, 199, 3000);
        check(counted.getCompleted().stream().mapToInt(CompletedSet::getReps).sum() == 199 && counted.getCompleted().stream().allMatch(x -> x.getReps() <= 100), "Aggregate count edits preserve valid per-round counts");
        WorkoutSession legacyRest = WorkoutEngine.INSTANCE.complete(start(new ExerciseSpec("卧推", 2, 10, 20, 2)), 1000);
        check(engine.tick(legacyRest, 8000).segmentSeconds(8000) == 5, "Automatic legacy rest expiry no longer includes rest in segment time");
        System.out.println(count + " cardio pause / phases / review / mixed-workout checks passed");
    }
}
