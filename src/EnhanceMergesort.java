
import java.util.Arrays;

public class EnhanceMergesort {

    public static void sort(int[] a, int cutoff) {
        if (cutoff < 0)
            throw new IllegalArgumentException("Invalid cutoff");

        int[] aux = new int[a.length];
        sort(a, aux, 0, a.length - 1, cutoff);
    }

    private static void sort(int[] a, int[] aux,
                             int lo, int hi, int cutoff) {

        if (lo >= hi) return;

        if (hi - lo + 1 <= cutoff) {
            insertion(a, lo, hi);
            return;
        }

        int mid = lo + (hi - lo) / 2;

        sort(a, aux, lo, mid, cutoff);
        sort(a, aux, mid + 1, hi, cutoff);

        merge(a, aux, lo, mid, hi);
    }

    private static void insertion(int[] a, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            for (int j = i; j > lo && a[j] < a[j - 1]; j--) {
                exch(a, j, j - 1);
            }
        }
    }

    private static void exch(int[] a, int i, int j) {
        int temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }

    private static void merge(int[] a, int[] aux,
                              int lo, int mid, int hi) {

        for (int k = lo; k <= hi; k++)
            aux[k] = a[k];

        int i = lo;
        int j = mid + 1;

        for (int k = lo; k <= hi; k++) {
            if (i > mid) a[k] = aux[j++];
            else if (j > hi) a[k] = aux[i++];
            else if (aux[j] < aux[i]) a[k] = aux[j++];
            else a[k] = aux[i++];
        }
    }

    public static void main(String[] args) {
        int[] a = {8, 3, 7, 1, 6, 2, 5, 4};

        sort(a, 4);

        System.out.println(Arrays.toString(a));
    }
}