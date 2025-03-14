package com.github.catvod.bean.alist;

import com.google.gson.Gson;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FileBasedList<T> implements List<T> {
    private final File file; // 存储数据的文件
    private final Gson gson; // Gson 用于序列化和反序列化
    private final Class<T> type; // 泛型类型
    private int size; // 当前列表的大小
    private final List<Long> linePositions; // 记录每一行的文件位置

    public FileBasedList(String filePath, Class<T> type) {
        this.file = new File(filePath);
        this.gson = new Gson();
        this.type = type;
        this.linePositions = new ArrayList<>();

        // 确保文件的父目录存在，如果不存在则创建所有缺失的父目录
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs(); // 创建所有缺失的父目录
            if (!dirsCreated) {
                throw new RuntimeException("Failed to create parent directories: " + parentDir.getAbsolutePath());
            }
        }

        // 如果文件不存在，则创建
        if (!file.exists()) {
            try {
                file.createNewFile();
                this.size = 0; // 新文件大小为 0
            } catch (IOException e) {
                throw new RuntimeException("Failed to create file: " + filePath, e);
            }
        } else {
            // 如果文件已存在，初始化文件位置和大小
            initializeLinePositions();
        }
    }

    // 不带文件名的构造函数，自动生成随机文件名
    public FileBasedList(Class<T> type) {
        this(generateRandomFileName(), type);
    }

    // 生成随机文件名
    private static String generateRandomFileName() {
        return com.github.catvod.utils.Path.root() + "/TV/list/" + UUID.randomUUID().toString() + ".list"; // 生成 UUID
                                                                                                           // 并添加 .list
                                                                                                           // 后缀
    }

    /**
     * 初始化文件位置和大小
     */
    private void initializeLinePositions() {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long position = randomAccessFile.getFilePointer();
            String line;
            while ((line = randomAccessFile.readLine()) != null) {
                linePositions.add(position); // 记录当前行的起始位置
                position = randomAccessFile.getFilePointer(); // 更新位置
            }
            this.size = linePositions.size();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize line positions", e);
        }
    }

    // List 接口方法实现

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        for (T item : this) {
            if (Objects.equals(item, o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            return new Iterator<T>() {
                private String nextLine = reader.readLine(); // 读取第一行

                @Override
                public boolean hasNext() {
                    return nextLine != null;
                }

                @Override
                public T next() {
                    try {
                        T item = gson.fromJson(nextLine, type); // 反序列化为对象
                        nextLine = reader.readLine(); // 读取下一行
                        return item;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read next line", e);
                    }
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize iterator", e);
        }
    }

    @Override
    public Object[] toArray() {
        List<T> list = new ArrayList<>();
        for (T item : this) {
            list.add(item);
        }
        return list.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        List<T> list = new ArrayList<>();
        for (T item : this) {
            list.add(item);
        }
        return list.toArray(a);
    }

    @Override
    public boolean add(T t) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.seek(randomAccessFile.length()); // 跳转到文件末尾
            long position = randomAccessFile.getFilePointer(); // 记录新行的起始位置
            String json = gson.toJson(t); // 序列化为 JSON
            randomAccessFile.writeBytes(json + System.lineSeparator()); // 追加一行
            linePositions.add(position); // 记录新行的起始位置
            size++; // 大小增加
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to file", e);
        }
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Remove operation is not supported.");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T item : c) {
            add(item);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException("AddAll at index is not supported.");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("RemoveAll operation is not supported.");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("RetainAll operation is not supported.");
    }

    @Override
    public void clear() {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(""); // 清空文件内容
            size = 0; // 大小重置为 0
            linePositions.clear(); // 清空文件位置记录
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear file", e);
        }
    }

    @Override
    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long position = linePositions.get(index); // 获取指定行的起始位置
            randomAccessFile.seek(position); // 跳转到指定位置
            String line = randomAccessFile.readLine(); // 读取一行
            return gson.fromJson(line, type); // 反序列化为对象
        } catch (IOException e) {
            throw new RuntimeException("Failed to read from file", e);
        }
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException("Set operation is not supported.");
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException("Add at index is not supported.");
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException("Remove at index is not supported.");
    }

    @Override
    public int indexOf(Object o) {
        int index = 0;
        for (T item : this) {
            if (Objects.equals(item, o)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int lastIndex = -1;
        int index = 0;
        for (T item : this) {
            if (Objects.equals(item, o)) {
                lastIndex = index;
            }
            index++;
        }
        return lastIndex;
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException("ListIterator is not supported.");
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException("ListIterator at index is not supported.");
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        List<T> subList = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            subList.add(get(i));
        }
        return subList;
    }

    // 其他方法

    public Stream<T> stream() {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false); // 不支持并行流
    }

    public static class IndexedItem<T> {
        private final T item;
        private final int lineNumber;

        public IndexedItem(T item, int lineNumber) {
            this.item = item;
            this.lineNumber = lineNumber;
        }

        public T getItem() {
            return item;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }

    public Stream<IndexedItem<T>> indexedStream() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            Spliterator<IndexedItem<T>> spliterator = Spliterators
                    .spliteratorUnknownSize(new Iterator<IndexedItem<T>>() {
                        private String nextLine = reader.readLine(); // 读取第一行
                        private int currentLineNumber = 0; // 当前行号

                        @Override
                        public boolean hasNext() {
                            return nextLine != null;
                        }

                        @Override
                        public IndexedItem<T> next() {
                            try {
                                T item = gson.fromJson(nextLine, type); // 反序列化为对象
                                IndexedItem<T> indexedItem = new IndexedItem<>(item, currentLineNumber);
                                nextLine = reader.readLine(); // 读取下一行
                                currentLineNumber++; // 行号增加
                                return indexedItem;
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to read next line", e);
                            }
                        }
                    }, Spliterator.ORDERED);

            return StreamSupport.stream(spliterator, false); // 不支持并行流
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize indexed stream", e);
        }
    }
}