# Unit Tests - Club Management System

## 📋 Mô tả

Bộ Unit Tests sử dụng **Mockito** để giả lập database, không cần kết nối thật. Tests bao gồm các luồng chính của hệ thống:

1. **Luồng tham gia CLB**: Sinh viên gửi đơn → Chủ tịch duyệt → Xác nhận thanh toán
2. **Luồng tạo CLB**: Sinh viên gửi đơn thành lập → Admin duyệt
3. **Thống kê & Báo cáo**: Dashboard admin với thống kê thành viên, doanh thu

## 📁 Cấu trúc Test Files

```
src/test/java/com/swp391/clubmanagement/service/
├── RegisterServiceTest.java              # Test luồng sinh viên gửi đơn gia nhập
├── LeaderRegisterServiceTest.java        # Test Chủ tịch duyệt đơn và xác nhận thanh toán  
├── ClubApplicationServiceTest.java       # Test luồng tạo CLB
└── AdminDashboardServiceTest.java        # Test thống kê và báo cáo
```

## 🧪 Test Cases Summary

### 1. RegisterServiceTest (7 test cases)
Kiểm tra luồng sinh viên gửi đơn gia nhập CLB

| Test Case | Mô tả |
|-----------|-------|
| TC01 | ✅ Sinh viên đăng ký tham gia CLB thành công |
| TC02 | ❌ Không thể đăng ký CLB khi đang chờ duyệt |
| TC03 | ❌ Không thể đăng ký CLB đã là thành viên |
| TC04 | ✅ Có thể tái gia nhập sau khi bị từ chối |
| TC05 | ✅ Xem danh sách các CLB đã đăng ký |
| TC06 | ❌ Package không tồn tại |
| TC07 | ❌ Package không active |

### 2. LeaderRegisterServiceTest (7 test cases)
Kiểm tra luồng Chủ tịch duyệt đơn và xác nhận thanh toán

| Test Case | Mô tả |
|-----------|-------|
| TC01 | ✅ Chủ tịch duyệt đơn thành công |
| TC02 | ✅ Chủ tịch từ chối đơn |
| TC03 | ✅ Xác nhận thanh toán thành công |
| TC04 | ❌ Không thể duyệt đơn đã được xử lý |
| TC05 | ❌ Không thể xác nhận thanh toán khi chưa duyệt |
| TC06 | ❌ User không phải Leader |
| TC07 | ✅ Xem danh sách đơn theo trạng thái |

### 3. ClubApplicationServiceTest (7 test cases)
Kiểm tra luồng sinh viên tạo CLB mới

| Test Case | Mô tả |
|-----------|-------|
| TC01 | ✅ Sinh viên gửi đơn thành lập CLB thành công |
| TC02 | ❌ Sinh viên đang là thành viên CLB không được tạo CLB |
| TC03 | ✅ Admin duyệt đơn thành lập CLB thành công |
| TC04 | ✅ Admin từ chối đơn thành lập CLB |
| TC05 | ❌ Không thể duyệt đơn đã được xử lý |
| TC06 | ✅ Xem danh sách đơn theo trạng thái |
| TC07 | ❌ Đơn không tồn tại |

### 4. AdminDashboardServiceTest (9 test cases)
Kiểm tra thống kê và báo cáo

| Test Case | Mô tả |
|-----------|-------|
| TC01 | ✅ Lấy tổng số CLB đang hoạt động |
| TC02 | ✅ Lấy tổng số thành viên (registrations) |
| TC03 | ✅ Lấy tổng số sinh viên duy nhất |
| TC04 | ✅ Thống kê CLB theo category |
| TC05 | ✅ Thống kê thành viên theo vai trò |
| TC06 | ✅ Top 5 CLB có nhiều thành viên nhất |
| TC07 | ✅ Danh sách CLB mới trong tháng |
| TC08 | ✅ Lấy dữ liệu tổng quan Dashboard |
| TC09 | ✅ Trường hợp không có CLB nào |

**Tổng cộng: 30 test cases**

## 🚀 Chạy Tests

### Chạy tất cả tests
```bash
mvn test
```

### Chạy tests của một class cụ thể
```bash
# Test luồng tham gia CLB
mvn test -Dtest=RegisterServiceTest

# Test luồng duyệt đơn
mvn test -Dtest=LeaderRegisterServiceTest

# Test luồng tạo CLB
mvn test -Dtest=ClubApplicationServiceTest

# Test thống kê
mvn test -Dtest=AdminDashboardServiceTest
```

### Chạy một test case cụ thể
```bash
mvn test -Dtest=RegisterServiceTest#testJoinClub_Success
```

### Chạy tests với báo cáo coverage (nếu có Jacoco)
```bash
mvn clean test jacoco:report
```

## 📊 Kết quả mong đợi

Khi chạy `mvn test`, bạn sẽ thấy output như sau:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.swp391.clubmanagement.service.RegisterServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.swp391.clubmanagement.service.LeaderRegisterServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.swp391.clubmanagement.service.ClubApplicationServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.swp391.clubmanagement.service.AdminDashboardServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

## 🛠️ Công nghệ sử dụng

- **JUnit 5** (Jupiter): Framework testing chính
- **Mockito**: Mock dependencies (repositories, mappers)
- **Spring Boot Test**: Test utilities và annotations
- **AssertJ**: Assertions (optional, có thể dùng JUnit assertions)

## 📖 Giải thích Mock

### Mockito Mock là gì?

**Mock** là đối tượng giả lập (fake object) thay thế cho dependencies thực (như database, external APIs). 

### Ưu điểm của Mock Testing

1. ✅ **Nhanh**: Không cần kết nối database thật
2. ✅ **Độc lập**: Tests không phụ thuộc vào dữ liệu database
3. ✅ **Kiểm soát**: Có thể giả lập mọi trường hợp (success, error, edge cases)
4. ✅ **Tách biệt**: Test từng service riêng lẻ, không bị ảnh hưởng bởi các service khác

### Ví dụ Mock

```java
// Mock repository
@Mock
private RegisterRepository registerRepository;

// Giả lập hành vi: Khi gọi findById(1) thì trả về testRegister
when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));

// Verify: Kiểm tra method save đã được gọi đúng 1 lần
verify(registerRepository, times(1)).save(any(Registers.class));
```

## 🔍 Debug Tests

### Xem log chi tiết
```bash
mvn test -X
```

### Chạy tests trong IntelliJ IDEA
1. Right-click vào test class → **Run 'RegisterServiceTest'**
2. Hoặc click vào icon ▶️ bên cạnh class/method

### Chạy tests trong VS Code
1. Cài extension: **Test Runner for Java**
2. Click vào icon ▶️ trong test file

## 📝 Lưu ý

1. **Không cần database**: Tests sử dụng mock, không kết nối MySQL
2. **Không cần server**: Tests chạy độc lập, không start Spring Boot
3. **Fast**: Tất cả 30 tests chạy trong vài giây
4. **Isolated**: Mỗi test độc lập, không ảnh hưởng lẫn nhau

## 🎯 Best Practices được áp dụng

1. ✅ **AAA Pattern**: Arrange (Given) → Act (When) → Assert (Then)
2. ✅ **Descriptive Names**: Tên test rõ ràng, mô tả hành vi
3. ✅ **Single Responsibility**: Mỗi test chỉ test 1 scenario
4. ✅ **Mock Dependencies**: Mock tất cả dependencies bên ngoài
5. ✅ **Verify Interactions**: Kiểm tra methods được gọi đúng cách
6. ✅ **Test Edge Cases**: Bao gồm cả trường hợp lỗi

## 📈 Code Coverage

Để xem code coverage, thêm plugin Jacoco vào `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Sau đó chạy:
```bash
mvn clean test jacoco:report
```

Báo cáo sẽ có tại: `target/site/jacoco/index.html`

## 🤝 Đóng góp

Khi thêm feature mới, hãy thêm tests tương ứng:
1. Tạo test class với suffix `*Test.java`
2. Mock tất cả dependencies
3. Viết test cases cho happy path và error cases
4. Đảm bảo tất cả tests pass trước khi commit

---

**Chúc bạn test thành công! 🎉**

