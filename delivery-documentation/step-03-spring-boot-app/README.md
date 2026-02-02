# Step 03: Spring Boot Application Development

## Overview
This step focuses on developing the Spring Boot REST API application that will be deployed through our DevSecOps pipeline.

## Objectives
- Create a complete Spring Boot REST API for Books management
- Implement proper layered architecture (Controller, Service, Model)
- Add comprehensive testing (Unit and Integration tests)
- Configure monitoring and health checks
- Implement security best practices

## Prerequisites
- Completed [Step 02: Project Structure Creation](../step-02-project-structure/)
- Java 17 installed
- Maven 3.9.x installed
- IDE (IntelliJ IDEA, VS Code, or Eclipse)

## Step-by-Step Implementation

### 1. Create the Main Application Class

```bash
cat > app/src/main/java/com/company/booksapi/BooksApiApplication.java << 'EOF'
package com.company.booksapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BooksApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BooksApiApplication.class, args);
    }
}
EOF
```

### 2. Create the Book Model

```bash
cat > app/src/main/java/com/company/booksapi/model/Book.java << 'EOF'
package com.company.booksapi.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class Book {
    
    @NotNull
    private Long id;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Author is required")
    private String author;
    
    @NotBlank(message = "ISBN is required")
    private String isbn;
    
    @Positive(message = "Price must be positive")
    private Double price;
    
    private String description;
    
    // Default constructor
    public Book() {}
    
    // Constructor with parameters
    public Book(Long id, String title, String author, String isbn, Double price, String description) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.price = price;
        this.description = description;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", isbn='" + isbn + '\'' +
                ", price=" + price +
                ", description='" + description + '\'' +
                '}';
    }
}
EOF
```

### 3. Create the Book Service

```bash
cat > app/src/main/java/com/company/booksapi/service/BookService.java << 'EOF'
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
EOF
```

### 4. Create the Books Controller

```bash
cat > app/src/main/java/com/company/booksapi/controller/BooksController.java << 'EOF'
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
EOF
```

### 5. Create Application Configuration

```bash
cat > app/src/main/resources/application.yml << 'EOF'
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: books-api
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:development}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${SPRING_PROFILES_ACTIVE:development}

logging:
  level:
    com.company.booksapi: INFO
    org.springframework: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

---
spring:
  config:
    activate:
      on-profile: development
  
logging:
  level:
    com.company.booksapi: DEBUG

---
spring:
  config:
    activate:
      on-profile: production

logging:
  level:
    com.company.booksapi: INFO
    org.springframework: WARN
EOF
```

### 6. Create Unit Tests

#### Controller Tests
```bash
cat > app/src/test/java/com/company/booksapi/controller/BooksControllerTest.java << 'EOF'
package com.company.booksapi.controller;

import com.company.booksapi.model.Book;
import com.company.booksapi.service.BookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BooksController.class)
class BooksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllBooks_ShouldReturnListOfBooks() throws Exception {
        // Given
        Book book1 = new Book(1L, "Test Book 1", "Author 1", "123456789", 29.99, "Description 1");
        Book book2 = new Book(2L, "Test Book 2", "Author 2", "987654321", 39.99, "Description 2");
        when(bookService.getAllBooks()).thenReturn(Arrays.asList(book1, book2));

        // When & Then
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Test Book 1"))
                .andExpect(jsonPath("$[1].title").value("Test Book 2"));
    }

    @Test
    void getBookById_WhenBookExists_ShouldReturnBook() throws Exception {
        // Given
        Book book = new Book(1L, "Test Book", "Test Author", "123456789", 29.99, "Test Description");
        when(bookService.getBookById(1L)).thenReturn(Optional.of(book));

        // When & Then
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Book"));
    }

    @Test
    void createBook_WithValidData_ShouldReturnCreatedBook() throws Exception {
        // Given
        Book inputBook = new Book(null, "New Book", "New Author", "123456789", 29.99, "New Description");
        Book createdBook = new Book(1L, "New Book", "New Author", "123456789", 29.99, "New Description");
        when(bookService.createBook(any(Book.class))).thenReturn(createdBook);

        // When & Then
        mockMvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputBook)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("New Book"));
    }

    @Test
    void health_ShouldReturnHealthyMessage() throws Exception {
        mockMvc.perform(get("/api/books/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Books API is healthy!"));
    }
}
EOF
```

#### Service Tests
```bash
cat > app/src/test/java/com/company/booksapi/service/BookServiceTest.java << 'EOF'
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
}
EOF
```

### 7. Build and Test the Application

```bash
# Navigate to app directory
cd app

# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package the application
mvn package

# Run the application locally (optional)
mvn spring-boot:run
```

### 8. Test API Endpoints

```bash
# Test health endpoint
curl http://localhost:8080/api/books/health

# Get all books
curl http://localhost:8080/api/books

# Get book by ID
curl http://localhost:8080/api/books/1

# Create a new book
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New Book",
    "author": "New Author",
    "isbn": "978-1234567890",
    "price": 29.99,
    "description": "A new book description"
  }'

# Search books by title
curl "http://localhost:8080/api/books/search?title=Spring"

# Get Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### 9. Commit the Application Code

```bash
# Add all changes
git add .

# Commit with descriptive message
git commit -m "Implement complete Spring Boot Books API

- Added Book model with validation annotations
- Implemented BookService with CRUD operations and search functionality
- Created BooksController with REST endpoints and monitoring metrics
- Added comprehensive unit tests for controller and service layers
- Configured application.yml with profiles and monitoring endpoints
- Integrated Prometheus metrics and health checks
- Added sample data initialization for testing"

# Push to repository
git push origin main
```

## API Endpoints Documentation

### Books API Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/books` | Get all books | None | List of books |
| GET | `/api/books/{id}` | Get book by ID | None | Book object |
| POST | `/api/books` | Create new book | Book JSON | Created book |
| PUT | `/api/books/{id}` | Update book | Book JSON | Updated book |
| DELETE | `/api/books/{id}` | Delete book | None | 204 No Content |
| GET | `/api/books/search` | Search books | Query params: title, author | List of books |
| GET | `/api/books/count` | Get book count | None | Number |
| GET | `/api/books/health` | Health check | None | String |

### Monitoring Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Application health |
| GET | `/actuator/metrics` | Application metrics |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/actuator/info` | Application info |

## Interview Questions & Answers

### Q1: Explain the architecture of your Spring Boot application?
**Answer**: "I implemented a layered architecture following Spring Boot best practices:

1. **Controller Layer**: `BooksController` handles HTTP requests, validates input, and returns responses. It includes monitoring metrics and proper error handling.

2. **Service Layer**: `BookService` contains business logic, data manipulation, and search functionality. It's thread-safe using ConcurrentHashMap.

3. **Model Layer**: `Book` entity with validation annotations ensures data integrity at the model level.

4. **Configuration**: Application.yml with profiles for different environments and comprehensive monitoring setup.

This separation ensures maintainability, testability, and follows single responsibility principle."

### Q2: How did you implement monitoring and observability?
**Answer**: "I integrated comprehensive monitoring:

1. **Prometheus Metrics**: Custom counters and timers for API requests using Micrometer
2. **Health Checks**: Spring Actuator endpoints for application health monitoring
3. **Logging**: Structured logging with different levels for development and production
4. **Performance Monitoring**: @Timed annotations on controller methods to track response times
5. **Custom Metrics**: Request counters to track API usage patterns

This provides full observability for production monitoring and alerting."

### Q3: What testing strategy did you implement?
**Answer**: "I implemented a comprehensive testing strategy:

1. **Unit Tests**: 
   - Service layer tests for business logic validation
   - Controller tests using MockMvc for HTTP layer testing
   - Mocking external dependencies for isolated testing

2. **Test Coverage**: JaCoCo integration for code coverage reporting
3. **Validation Testing**: Testing input validation and error scenarios
4. **Integration Testing**: Testing complete request-response cycles

This ensures high code quality and confidence in deployments."

### Q4: How did you handle security in the application?
**Answer**: "I implemented several security measures:

1. **Input Validation**: Bean validation annotations on the model
2. **CORS Configuration**: Controlled cross-origin requests
3. **Non-root Container**: Docker configuration runs as non-privileged user
4. **Read-only Filesystem**: Container security with read-only root filesystem
5. **Health Endpoint Security**: Separate health endpoint for monitoring
6. **Error Handling**: Proper error responses without exposing internal details

These measures follow security best practices for production deployments."

## Next Steps

After completing this step:
1. Spring Boot application is fully functional with REST API
2. Comprehensive testing is in place
3. Monitoring and health checks are configured
4. Application is ready for containerization
5. Ready to proceed to [Step 04: AWS Prerequisites Setup](../step-04-aws-prerequisites/)

## Commands Summary

```bash
# Build and test
cd app
mvn clean compile
mvn test
mvn package

# Run locally
mvn spring-boot:run

# Test endpoints
curl http://localhost:8080/api/books/health
curl http://localhost:8080/api/books
curl http://localhost:8080/actuator/prometheus

# Commit changes
git add .
git commit -m "Implement complete Spring Boot Books API"
git push origin main
```

This completes Step 03: Spring Boot Application Development. The application is now ready for the next phase of containerization and infrastructure setup.