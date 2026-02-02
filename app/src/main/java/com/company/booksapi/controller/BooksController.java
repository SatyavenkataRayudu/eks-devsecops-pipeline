package com.company.booksapi.controller;

import com.company.booksapi.model.Book;
import com.company.booksapi.service.BookService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")
public class BooksController {
    
    private final BookService bookService;
    private final Counter bookRequestCounter;
    
    @Autowired
    public BooksController(BookService bookService, MeterRegistry meterRegistry) {
        this.bookService = bookService;
        this.bookRequestCounter = Counter.builder("books_requests_total")
                .description("Total number of requests to books API")
                .register(meterRegistry);
    }
    
    @GetMapping
    @Timed(value = "books.get.all", description = "Time taken to get all books")
    public ResponseEntity<List<Book>> getAllBooks() {
        bookRequestCounter.increment();
        List<Book> books = bookService.getAllBooks();
        return ResponseEntity.ok(books);
    }
    
    @GetMapping("/{id}")
    @Timed(value = "books.get.by.id", description = "Time taken to get book by ID")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        bookRequestCounter.increment();
        Optional<Book> book = bookService.getBookById(id);
        return book.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @Timed(value = "books.create", description = "Time taken to create a book")
    public ResponseEntity<Book> createBook(@Valid @RequestBody Book book) {
        bookRequestCounter.increment();
        Book createdBook = bookService.createBook(book);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBook);
    }
    
    @PutMapping("/{id}")
    @Timed(value = "books.update", description = "Time taken to update a book")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @Valid @RequestBody Book book) {
        bookRequestCounter.increment();
        Optional<Book> updatedBook = bookService.updateBook(id, book);
        return updatedBook.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    @Timed(value = "books.delete", description = "Time taken to delete a book")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookRequestCounter.increment();
        boolean deleted = bookService.deleteBook(id);
        return deleted ? ResponseEntity.noContent().build() 
                      : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/search")
    @Timed(value = "books.search", description = "Time taken to search books")
    public ResponseEntity<List<Book>> searchBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author) {
        bookRequestCounter.increment();
        
        List<Book> books;
        if (title != null && !title.isEmpty()) {
            books = bookService.searchBooksByTitle(title);
        } else if (author != null && !author.isEmpty()) {
            books = bookService.searchBooksByAuthor(author);
        } else {
            books = bookService.getAllBooks();
        }
        
        return ResponseEntity.ok(books);
    }
    
    @GetMapping("/count")
    @Timed(value = "books.count", description = "Time taken to get book count")
    public ResponseEntity<Long> getBookCount() {
        bookRequestCounter.increment();
        long count = bookService.getBookCount();
        return ResponseEntity.ok(count);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Books API is healthy!");
    }
}