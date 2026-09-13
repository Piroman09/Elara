package android.util;

/**
 * Minimal stand-in for the platform class, shadowing the "mockable" android.jar
 * on the local unit-test classpath.
 *
 * media3 backs `Player.Commands` with a `SparseBooleanArray`, and the mockable
 * android.jar throws `RuntimeException: Method ... not mocked` for every member
 * it is asked to run — which made `Player.Commands` impossible to construct in a
 * plain JVM test, and therefore made `QueueAwarePlayer` untestable.
 *
 * That matters more than usual here: the Android emulator cannot run in this
 * environment, so JVM tests are the only automated verification available. Test
 * classes are loaded ahead of the mockable jar, so this implementation wins.
 *
 * Only the members media3's `FlagSet` actually touches are implemented.
 */
public class SparseBooleanArray {

    private int[] keys = new int[0];
    private boolean[] values = new boolean[0];
    private int size = 0;

    public SparseBooleanArray() {
    }

    public SparseBooleanArray(int initialCapacity) {
        if (initialCapacity > 0) {
            keys = new int[initialCapacity];
            values = new boolean[initialCapacity];
        }
    }

    private void ensureCapacity(int capacity) {
        if (capacity <= keys.length) {
            return;
        }
        int[] grownKeys = new int[capacity];
        boolean[] grownValues = new boolean[capacity];
        System.arraycopy(keys, 0, grownKeys, 0, size);
        System.arraycopy(values, 0, grownValues, 0, size);
        keys = grownKeys;
        values = grownValues;
    }

    public void append(int key, boolean value) {
        int existing = indexOfKey(key);
        if (existing >= 0) {
            values[existing] = value;
            return;
        }
        ensureCapacity(size + 1);
        keys[size] = key;
        values[size] = value;
        size++;
    }

    public void put(int key, boolean value) {
        append(key, value);
    }

    public boolean get(int key) {
        return get(key, false);
    }

    public boolean get(int key, boolean valueIfKeyNotFound) {
        int index = indexOfKey(key);
        return index >= 0 ? values[index] : valueIfKeyNotFound;
    }

    public void delete(int key) {
        int index = indexOfKey(key);
        if (index < 0) {
            return;
        }
        for (int i = index; i < size - 1; i++) {
            keys[i] = keys[i + 1];
            values[i] = values[i + 1];
        }
        size--;
    }

    public int size() {
        return size;
    }

    public int keyAt(int index) {
        return keys[index];
    }

    public boolean valueAt(int index) {
        return values[index];
    }

    public int indexOfKey(int key) {
        for (int i = 0; i < size; i++) {
            if (keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    public void clear() {
        size = 0;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(keys[i]).append('=').append(values[i]);
        }
        return out.append('}').toString();
    }
}
