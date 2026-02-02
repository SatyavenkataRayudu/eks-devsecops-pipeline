package com.company.booksapi.service;

import com.company.booksapi.model.Book;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BookService {
    
    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    public BookService() {
        // Initialize with sample data
        initializeSampleData();
    }
    
    private void initializeSampleData() {
        createBook(new Book(null, "The Spring Boot Guide", "John Doe", "978-1234567890", 29.99, "Comprehensive guide to Spring Boot"));
        createBook(new Book(null, "Kubernetes in Action", "Jane Smith", "978-0987654321", 39.99, "Learn Kubernetes from scratch"));
        createBook(new Book(null, "DevOps Handbook", "Mike Johnson", "978-1122334455", 34.99, "DevOps best practices"));
    }
    
    public List<Book> getAllBooks() {
        return new ArrayList<>(books.values());
    }
    
    public Optional<Book> getBookById(Long id) {
        return Optional.ofNullable(books.get(id));
    }
    
    public Book createBook(Book book) {
        if (book.getId() == null) {
            book.setId(idGenerator.getAndIncrement());
        }
        books.put(book.getId(), book);
        return book;
    }
    
    public Optional<Book> updateBook(Long id, Book updatedBook) {
        if (books.containsKey(id)) {
            updatedBook.setId(id);
            books.put(id, updatedBook);
            return Optional.of(updatedBook);
        }
        return Optional.empty();
    }
    
    public boolean deleteBook(Long id) {
        return books.remove(id) != null;
    }
    
    public List<Book> searchBooksByTitle(String title) {
        return books.values().stream()
                .filter(book -> book.getTitle().toLowerCase().contains(title.toLowerCase()))
                .toList();
    }
    
    public List<Book> searchBooksByAuthor(String author) {
        return books.values().stream()
                .filter(book -> book.getAuthor().toLowerCase().contains(author.toLowerCase()))
                .toList();
    }
    
    public long getBookCount() {
        return books.size();
    }
}