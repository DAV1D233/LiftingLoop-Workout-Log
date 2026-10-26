import com.fitlog.app.domain.DockMenuPolicy;

/** A drag saturates at the top and responds to reversal without hidden excess travel. */
public final class DockMenuPolicyCheck {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError(); checks++; }
    public static void main(String[] args) {
        DockMenuPolicy policy = DockMenuPolicy.INSTANCE;
        for (int count : new int[] { 1, 3, 4, 7 }) {
            float cursor = -1f;
            check(policy.selected(cursor, count) == -1);
            cursor = policy.advance(cursor, 13f, 52f, count);
            check(policy.selected(cursor, count) == -1);
            cursor = policy.advance(cursor, 14f, 52f, count);
            check(policy.selected(cursor, count) == 0);
            cursor = policy.advance(cursor, 100000f, 52f, count);
            check(policy.selected(cursor, count) == count - 1);
            cursor = policy.advance(cursor, 100000f, 52f, count);
            check(cursor == count - 1);
            cursor = policy.advance(cursor, -52f, 52f, count);
            check(policy.selected(cursor, count) == count - 2);
            cursor = policy.advance(cursor, -100000f, 52f, count);
            check(policy.selected(cursor, count) == -1);
            cursor = policy.advance(cursor, 52f, 52f, count);
            check(policy.selected(cursor, count) == 0);
        }
        for (int index = 0; index < 4; index++) {
            check(policy.selected(policy.advance(index, 0f, 52f, 4), 4) == index);
            check(policy.selected(policy.advance(index, 52f, 52f, 4), 4) == Math.min(index + 1, 3));
        }
        check(policy.selected(Float.NaN, 4) == -1);
        check(policy.selected(0f, 0) == -1);
        check(policy.advance(0f, 52f, 0f, 4) == -1f);
        check(policy.advance(0f, Float.POSITIVE_INFINITY, 52f, 4) == -1f);
        check(policy.advance(0f, 52f, 52f, 0) == -1f);
        System.out.println("Dock selection and edge reversal: " + checks + " checks passed");
    }
}
