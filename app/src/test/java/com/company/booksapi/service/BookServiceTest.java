package com.company.booksapi.service;

import com.company.booksapi.model.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BookServiceTest {

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService();
    }

    @Test
    void getAllBooks_ShouldReturnInitialBooks() {
        // When
        List<Book> books = bookService.getAllBooks();

        // Then
        assertNotNull(books);
        assertEquals(3, books.size()); // Initial sample data
    }

    @Test
    void createBook_ShouldAddNewBook() {
        // Given
        Book newBook = new Book(null, "Test Book", "Test Author", "123456789", 29.99, "Test Description");

        // When
        Book createdBook = bookService.createBook(newBook);

        // Then
        assertNotNull(createdBook);
        assertNotNull(createdBook.getId());
        assertEquals("Test Book", createdBook.getTitle());
        assertEquals("Test Author", createdBook.getAuthor());
    }

    @Test
    void getBookById_WhenBookExists_ShouldReturnBook() {
        // Given
        Book newBook = new Book(null, "Test Book", "Test Author", "123456789", 29.99, "Test Description");
        Book createdBook = bookService.createBook(newBook);

        // When
        Optional<Book> foundBook = bookService.getBookById(createdBook.getId());

        // Then
        assertTrue(foundBook.isPresent());
        assertEquals("Test Book", foundBook.get().getTitle());
    }

    @Test
    void getBookById_WhenBookNotExists_ShouldReturnEmpty() {
        // When
        Optional<Book> foundBook = bookService.getBookById(999L);

        // Then
        assertFalse(foundBook.isPresent());
    }

    @Test
    void updateBook_WhenBookExists_ShouldUpdateBook() {
        // Given
        Book newBook = new Book(null, "Original Title", "Original Author", "123456789", 29.99, "Original Description");
        Book createdBook = bookService.createBook(newBook);
        
        Book updatedBook = new Book(null, "Updated Title", "Updated Author", "987654321", 39.99, "Updated Description");

        // When
        Optional<Book> result = bookService.updateBook(createdBook.getId(), updatedBook);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Updated Title", result.get().getTitle());
        assertEquals("Updated Author", result.get().getAuthor());
        assertEquals(createdBook.getId(), result.get().getId());
    }

    @Test
    void deleteBook_WhenBookExists_ShouldReturnTrue() {
        // Given
        Book newBook = new Book(null, "Test Book", "Test Author", "123456789", 29.99, "Test Description");
        Book createdBook = bookService.createBook(newBook);

        // When
        boolean deleted = bookService.deleteBook(createdBook.getId());

        // Then
        assertTrue(deleted);
        assertFalse(bookService.getBookById(createdBook.getId()).isPresent());
    }

    @Test
    void searchBooksByTitle_ShouldReturnMatchingBooks() {
        // Given
        bookService.createBook(new Book(null, "Java Programming", "Author 1", "123456789", 29.99, "Description"));
        bookService.createBook(new Book(null, "Python Programming", "Author 2", "987654321", 39.99, "Description"));

        // When
        List<Book> javaBooks = bookService.searchBooksByTitle("Java");

        // Then
        assertEquals(1, javaBooks.size());
        assertEquals("Java Programming", javaBooks.get(0).getTitle());
    }

    @Test
    void getBookCount_ShouldReturnCorrectCount() {
        // Given
        long initialCount = bookService.getBookCount();
        bookService.createBook(new Book(null, "Test Book", "Test Author", "123456789", 29.99, "Description"));

        // When
        long newCount = bookService.getBookCount();

        // Then
        assertEquals(initialCount + 1, newCount);
    }
}