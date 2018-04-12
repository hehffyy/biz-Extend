package com.butone.sort;


@SuppressWarnings("unchecked")

public class SortUtils {
	private static int MAX_STACK_SIZE = 4096;
	private static int THRESHOLD = 10;

	public final static int INSERT = 1;
	public final static int BUBBLE = 2;
	public final static int SELECTION = 3;
	public final static int SHELL = 4;
	public final static int QUICK = 5;
	public final static int IMPROVED_QUICK = 6;

	public final static int MERGE = 7;
	public final static int IMPROVED_MERGE = 8;
	public final static int HEAP = 9;

	
	@SuppressWarnings("rawtypes")
	public static void sort(Object[] data, CompareCallBack c) {
		sort(IMPROVED_QUICK, data, c);
	}

	private static String[] name = { "insert", "bubble", "selection", "shell",
			"quick", "improved_quick", "merge", "improved_merge", "heap" };

	private static Sort[] impl = new Sort[] { new InsertSort(),
			new BubbleSort(), new SelectionSort(), new ShellSort(),
			new QuickSort(), new ImprovedQuickSort(), new MergeSort(),
			new ImprovedMergeSort(), new HeapSort() };

	public static String toString(int algorithm) {
		return name[algorithm - 1];
	}

	public static void sort(int algorithm, Object[] data, CompareCallBack<?> c) {
		impl[algorithm - 1].sort(data, c);
	}

	public static interface Sort {
		public void sort(Object[] data, CompareCallBack<?> c);
	}

	public static void swap(Object[] data, int i, int j) {
		Object temp = data[i];
		data[i] = data[j];
		data[j] = temp;
	}

	/**
	 * 插入排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class InsertSort implements SortUtils.Sort {
		public void sort(Object[] data, CompareCallBack c) {
			for (int i = 1; i < data.length; i++) {
				for (int j = i; (j > 0)
						&& (c.compare(data[j], data[j - 1]) < 0); j--) {
					SortUtils.swap(data, j, j - 1);
				}
			}
		}

	}

	/**
	 * 冒泡排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class BubbleSort implements SortUtils.Sort {
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.rut.util.algorithm.SortUtil.Sort#sort(int[])
		 */
		public void sort(Object[] data, CompareCallBack c) {
			for (int i = 0; i < data.length; i++) {
				for (int j = data.length - 1; j > i; j--) {
					if (c.compare(data[j], data[j - 1]) < 0) {
						SortUtils.swap(data, j, j - 1);
					}

				}
			}
		}

	}

	/**
	 * 选择排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class SelectionSort implements SortUtils.Sort {
		public void sort(Object[] data, CompareCallBack c) {
			for (int i = 0; i < data.length; i++) {
				int lowIndex = i;
				for (int j = data.length - 1; j > i; j--) {
					if (c.compare(data[j], data[lowIndex]) < 0) {

						lowIndex = j;
					}
				}
				SortUtils.swap(data, i, lowIndex);
			}
		}

	}

	/**
	 * Shell排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class ShellSort implements SortUtils.Sort {
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.rut.util.algorithm.SortUtil.Sort#sort(int[])
		 */
		public void sort(Object[] data, CompareCallBack c) {
			for (int i = data.length / 2; i > 2; i /= 2) {
				for (int j = 0; j < i; j++) {
					insertSort(data, c, j, i);
				}
			}
			insertSort(data, c, 0, 1);
		}

		/**
		 * @param data
		 * @param j
		 * @param i
		 */
		private void insertSort(Object[] data, CompareCallBack c, int start,
				int inc) {
			for (int i = start + inc; i < data.length; i += inc) {
				for (int j = i; (j >= inc)
						&& (c.compare(data[j], data[j - inc]) < 0); j -= inc) {
					SortUtils.swap(data, j, j - inc);
				}
			}
		}

	}

	/**
	 * 快速排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class QuickSort implements SortUtils.Sort {

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.rut.util.algorithm.SortUtil.Sort#sort(int[])
		 */
		public void sort(Object[] data, CompareCallBack c) {
			quickSort(data, c, 0, data.length - 1);
		}

		private void quickSort(Object[] data, CompareCallBack c, int i, int j) {
			int pivotIndex = (i + j) / 2;
			// swap
			SortUtils.swap(data, pivotIndex, j);

			int k = partition(data, c, i - 1, j, data[j]);

			SortUtils.swap(data, k, j);
			if ((k - i) > 1)
				quickSort(data, c, i, k - 1);
			if ((j - k) > 1)
				quickSort(data, c, k + 1, j);

		}
		private int partition(Object[] data, CompareCallBack c, int l, int r,
				Object pivot) {
			do {
				while (c.compare(data[++l], pivot) < 0)
					;
				while ((r != 0) && c.compare(data[--r], pivot) > 0)
					;
				SortUtils.swap(data, l, r);
			} while (l < r);
			SortUtils.swap(data, l, r);
			return l;
		}
	}

	/**
	 * 改进后快速排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class ImprovedQuickSort implements SortUtils.Sort {

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.rut.util.algorithm.SortUtil.Sort#sort(int[])
		 */
		public void sort(Object[] data, CompareCallBack c) {
			int[] stack = new int[MAX_STACK_SIZE];

			int top = -1;
			Object pivot;
			int pivotIndex, l, r;

			stack[++top] = 0;

			stack[++top] = data.length - 1;

			while (top > 0) {
				int j = stack[top--];
				int i = stack[top--];

				pivotIndex = (i + j) / 2;
				pivot = data[pivotIndex];

				SortUtils.swap(data, pivotIndex, j);

				// partition
				l = i - 1;
				r = j;
				do {
					while (c.compare(data[++l], pivot) < 0)
						;
					while ((r != 0) && c.compare(data[--r], pivot) > 0)
						;
					SortUtils.swap(data, l, r);
				} while (l < r);
				SortUtils.swap(data, l, r);
				SortUtils.swap(data, l, j);

				if ((l - i) > THRESHOLD) {
					stack[++top] = i;
					stack[++top] = l - 1;
				}
				if ((j - l) > THRESHOLD) {
					stack[++top] = l + 1;
					stack[++top] = j;
				}

			}
			insertSort(data, c);
		}

		/**
		 * @param data
		 */
		
		private void insertSort(Object[] data, CompareCallBack c) {
			for (int i = 1; i < data.length; i++) {
				for (int j = i; (j > 0)
						&& (c.compare(data[j], data[j - 1]) < 0); j--) {
					SortUtils.swap(data, j, j - 1);
				}
			}
		}
	}

	/**
	 * 并归排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class MergeSort implements SortUtils.Sort {
		
		public void sort(Object[] data, CompareCallBack c) {
			Object[] temp = new Object[data.length];
			mergeSort(data, c, temp, 0, data.length - 1);
		}
		private void mergeSort(Object[] data, CompareCallBack c, Object[] temp,
				int l, int r) {
			int mid = (l + r) / 2;
			if (l == r)
				return;
			mergeSort(data, c, temp, l, mid);
			mergeSort(data, c, temp, mid + 1, r);
			for (int i = l; i <= r; i++) {
				temp[i] = data[i];
			}
			int i1 = l;
			int i2 = mid + 1;
			for (int cur = l; cur <= r; cur++) {
				if (i1 == mid + 1)
					data[cur] = temp[i2++];
				else if (i2 > r)
					data[cur] = temp[i1++];
				else if (c.compare(temp[i1], temp[i2]) < 0)
					data[cur] = temp[i1++];
				else

					data[cur] = temp[i2++];
			}
		}

	}

	/**
	 * 改进后并归排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class ImprovedMergeSort implements SortUtils.Sort {

		public void sort(Object[] data, CompareCallBack c) {
			Object[] temp = new Object[data.length];
			mergeSort(data, c, temp, 0, data.length - 1);
		}

		private void mergeSort(Object[] data, CompareCallBack c, Object[] temp,
				int l, int r) {
			int i, j, k;
			int mid = (l + r) / 2;
			if (l == r)
				return;
			if ((mid - l) >= THRESHOLD)
				mergeSort(data, c, temp, l, mid);
			else
				insertSort(data, c, l, mid - l + 1);
			if ((r - mid) > THRESHOLD)
				mergeSort(data, c, temp, mid + 1, r);
			else
				insertSort(data, c, mid + 1, r - mid);

			for (i = l; i <= mid; i++) {
				temp[i] = data[i];
			}
			for (j = 1; j <= r - mid; j++) {
				temp[r - j + 1] = data[j + mid];
			}
			Object a = temp[l];
			Object b = temp[r];
			for (i = l, j = r, k = l; k <= r; k++) {
				if (c.compare(a, b) < 0) {
					data[k] = temp[i++];
					a = temp[i];
				} else {
					data[k] = temp[j--];
					b = temp[j];
				}
			}
		}

		/**
		 * @param data
		 * @param l
		 * @param i
		 */
		private void insertSort(Object[] data, CompareCallBack c, int start,
				int len) {
			for (int i = start + 1; i < start + len; i++) {
				for (int j = i; (j > start)
						&& c.compare(data[j], data[j - 1]) < 0; j--) {
					SortUtils.swap(data, j, j - 1);
				}
			}
		}
	}
	@SuppressWarnings("rawtypes")
	private static class MaxHeap {

		void init(Object[] data, CompareCallBack c) {
			this.queue = new Object[data.length + 1];
			for (int i = 0; i < data.length; i++) {
				queue[++size] = data[i];
				fixUp(size, c);
			}
		}

		private int size = 0;

		private Object[] queue;

		public void remove(CompareCallBack c) {
			SortUtils.swap(queue, 1, size--);
			fixDown(1, c);
		}

		// fixdown
		private void fixDown(int k, CompareCallBack c) {
			int j;
			while ((j = k << 1) <= size) {
				if (j < size && c.compare(queue[j], queue[j + 1]) < 0)
					j++;
				if (c.compare(queue[k], queue[j]) > 0) // 不用交换
					break;
				SortUtils.swap(queue, j, k);
				k = j;
			}
		}

		private void fixUp(int k, CompareCallBack c) {
			while (k > 1) {
				int j = k >> 1;
				if (c.compare(queue[j], queue[k]) > 0)
					break;
				SortUtils.swap(queue, j, k);
				k = j;
			}
		}

	}

	/**
	 * 堆排序
	 * 
	 * @author Administrator
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public static class HeapSort implements SortUtils.Sort {
		public void sort(Object[] data, CompareCallBack c) {
			MaxHeap h = new MaxHeap();
			h.init(data, c);
			for (int i = 0; i < data.length; i++)
				h.remove(c);
			System.arraycopy(h.queue, 1, data, 0, data.length);
		}

	}
}
