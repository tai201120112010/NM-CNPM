package refactor_code;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class PersonalTaskManagerViolations {

    private static final String DB_FILE_PATH = "tasks_database.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private long nextTaskId;

    // Constructor để khởi tạo nextTaskId
    public PersonalTaskManagerViolations() {
        this.nextTaskId = initializeNextTaskId();
    }

    // Phương thức trợ giúp để tải dữ liệu (sẽ được gọi lặp lại)
    private static JSONArray loadTasksFromDb() {
        JSONParser parser = new JSONParser();
        try (FileReader reader = new FileReader(DB_FILE_PATH)) {
            Object obj = parser.parse(reader);
            if (obj instanceof JSONArray) {
                return (JSONArray) obj;
            }
        } catch (IOException | ParseException e) {
            System.err.println("Lỗi khi đọc file database: " + e.getMessage());
        }
        return new JSONArray();
    }

    // Phương thức Khởi tạo nextTaskId bằng cách tìm ID lớn nhất hiện có trong DB.
    private long initializeNextTaskId() {
        JSONArray tasks = loadTasksFromDb();
        long maxId = 0;
        for (Object obj : tasks) {
            JSONObject existingTask = (JSONObject) obj;
            Object idObj = existingTask.get("id");
            if (idObj instanceof Number) {
                maxId = Math.max(maxId, ((Number) idObj).longValue());
            }
        }
        return maxId + 1;
    }

    // Phương thức trợ giúp để lưu dữ liệu
    private static void saveTasksToDb(JSONArray tasksData) {
        try (FileWriter file = new FileWriter(DB_FILE_PATH)) {
            file.write(tasksData.toJSONString());
            file.flush();
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi vào file database: " + e.getMessage());
        }
    }

    /**
     * Phương thức kiểm tra và xác thực dữ liệu đầu vào của nhiệm vụ.
     *
     * @param title Tiêu đề nhiệm vụ.
     * @param dueDateStr Ngày đến hạn (định dạng YYYY-MM-DD).
     * @param priorityLevel Mức độ ưu tiên ("Thấp", "Trung bình", "Cao").
     * @param isRecurring Boolean có phải là nhiệm vụ lặp lại không.
     * @return Đối tượng LocalDate của ngày đến hạn nếu hợp lệ, nếu không thì null.
     */
    private LocalDate validateTaskInput(String title, String dueDateStr, String priorityLevel,
                                        boolean isRecurring) {
        if (title == null || title.trim().isEmpty()) {
            System.out.println("Lỗi: Tiêu đề không được để trống.");
            return null;
        }
        if (dueDateStr == null || dueDateStr.trim().isEmpty()) {
            System.out.println("Lỗi: Ngày đến hạn không được để trống.");
            return null;
        }
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(dueDateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            System.out.println("Lỗi: Ngày đến hạn không hợp lệ. Vui lòng sử dụng định dạng YYYY-MM-DD.");
            return null;
        }
        String[] validPriorities = {"Thấp", "Trung bình", "Cao"};
        boolean isValidPriority = false;
        for (String validP : validPriorities) {
            if (validP.equals(priorityLevel)) {
                isValidPriority = true;
                break;
            }
        }
        if (!isValidPriority) {
            System.out.println("Lỗi: Mức độ ưu tiên không hợp lệ. Vui lòng chọn từ: Thấp, Trung bình, Cao.");
            return null;
        }

     
        return dueDate;
    }

   
    private boolean checkDuplicateTask(String title, LocalDate dueDate, JSONArray tasks) {
        for (Object obj : tasks) {
            JSONObject existingTask = (JSONObject) obj;
            if (existingTask.get("title").toString().equalsIgnoreCase(title) &&
                existingTask.get("due_date").toString().equals(dueDate.format(DATE_FORMATTER))) {
                System.out.println(String.format("Lỗi: Nhiệm vụ '%s' đã tồn tại với cùng ngày đến hạn.", title));
                return true;
            }
        }
        return false;
    }

    
    public JSONObject addNewTaskWithViolations(String title, String description,
                                               String dueDateStr, String priorityLevel,
                                               boolean isRecurring) {

        // 1. Phương thức kiểm tra đầu vào
        LocalDate dueDate = validateTaskInput(title, dueDateStr, priorityLevel, isRecurring); 
        if (dueDate == null) {
            return null;
        }

        // Tải dữ liệu
        JSONArray tasks = loadTasksFromDb();

        // 2. Phương thức kiểm tra dữ liệu trùng lặp
        if (checkDuplicateTask(title, dueDate, tasks)) {
            return null;
        }

        // Tạo ID nhiệm vụ
        long taskId = this.nextTaskId++;

        JSONObject newTask = new JSONObject();
        newTask.put("id", taskId);
        newTask.put("title", title);
        newTask.put("description", description);
        newTask.put("due_date", dueDate.format(DATE_FORMATTER));
        newTask.put("priority", priorityLevel);
        newTask.put("status", "Chưa hoàn thành");
        newTask.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        newTask.put("last_updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        newTask.put("is_recurring", isRecurring);
        if (isRecurring) {
            newTask.put("recurrence_pattern", "DAILY");
        }

        tasks.add(newTask);

        // Lưu dữ liệu
        saveTasksToDb(tasks);

        System.out.println(String.format("Đã thêm nhiệm vụ mới thành công với ID: %d", taskId));
        return newTask;
    }

    /**
     * Phương thức hoàn thành một nhiệm vụ và tạo phiên bản tiếp theo nếu nó là nhiệm vụ lặp lại.
     *
     * @param taskId ID của nhiệm vụ cần hoàn thành.
     * @return JSONObject của nhiệm vụ mới được tạo (nếu có), hoặc null.
     */
    public JSONObject completeTaskAndGenerateNextInstance(long taskId) {
        JSONArray tasks = loadTasksFromDb();
        JSONObject completedTask = null;

        // Tìm nhiệm vụ cần hoàn thành
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = (JSONObject) tasks.get(i);
            Object idObj = task.get("id");
            if (idObj instanceof Number && ((Number) idObj).longValue() == taskId) {
                completedTask = task;
                break;
            }
        }

        if (completedTask == null) {
            System.out.println(String.format("Lỗi: Không tìm thấy nhiệm vụ với ID: %d", taskId));
            return null;
        }

        // Cập nhật trạng thái nhiệm vụ hiện tại thành "Hoàn thành"
        completedTask.put("status", "Hoàn thành");
        completedTask.put("last_updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        System.out.println(String.format("Nhiệm vụ ID %d ('%s') đã được đánh dấu là 'Hoàn thành'.",
                                         taskId, completedTask.get("title")));

        boolean isRecurring = (boolean) completedTask.getOrDefault("is_recurring", false);
        if (isRecurring) {
            String recurrencePattern = (String) completedTask.getOrDefault("recurrence_pattern", "DAILY"); // <--- SỬA ĐỔI: Mặc định là DAILY nếu không có
            LocalDate currentDueDate = LocalDate.parse((String) completedTask.get("due_date"), DATE_FORMATTER);
            LocalDate newDueDate = null;

            // Tính toán ngày đến hạn mới dựa trên mẫu lặp lại
            switch (recurrencePattern.toUpperCase()) {
                case "DAILY":
                    newDueDate = currentDueDate.plusDays(1);
                    break;
                case "WEEKLY":
                    newDueDate = currentDueDate.plusWeeks(1);
                    break;
                case "MONTHLY":
                    newDueDate = currentDueDate.plusMonths(1);
                    break;
                default:
                    System.out.println(String.format("Cảnh báo: Mẫu lặp lại không xác định '%s' cho nhiệm vụ ID %d. Không tạo phiên bản mới.",
                                                     recurrencePattern, taskId));
                    saveTasksToDb(tasks);
                    return null;
            }

            // Tạo một phiên bản nhiệm vụ mới
            JSONObject nextInstanceTask = new JSONObject();
            nextInstanceTask.put("id", this.nextTaskId++);
            nextInstanceTask.put("title", completedTask.get("title"));
            nextInstanceTask.put("description", completedTask.get("description"));
            nextInstanceTask.put("due_date", newDueDate.format(DATE_FORMATTER));
            nextInstanceTask.put("priority", completedTask.get("priority"));
            nextInstanceTask.put("status", "Chưa hoàn thành"); // Phiên bản mới bắt đầu là chưa hoàn thành
            nextInstanceTask.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            nextInstanceTask.put("last_updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            nextInstanceTask.put("is_recurring", true); // Phiên bản mới vẫn là lặp lại
            nextInstanceTask.put("recurrence_pattern", recurrencePattern);

            tasks.add(nextInstanceTask); // Thêm nhiệm vụ mới vào danh sách
            saveTasksToDb(tasks); // Lưu toàn bộ danh sách đã cập nhật

            System.out.println(String.format("Đã tạo phiên bản tiếp theo của nhiệm vụ '%s' với ID mới: %d, ngày đến hạn: %s",
                                             nextInstanceTask.get("title"), nextInstanceTask.get("id"), newDueDate.format(DATE_FORMATTER)));
            return nextInstanceTask;
        } else {
            saveTasksToDb(tasks); // Lưu trạng thái hoàn thành nếu không lặp lại
            System.out.println(String.format("Nhiệm vụ ID %d không phải là nhiệm vụ lặp lại. Không tạo phiên bản mới.", taskId));
            return null;
        }
    }

    public static void main(String[] args) {
        PersonalTaskManagerViolations manager = new PersonalTaskManagerViolations();
        System.out.println("\nThêm nhiệm vụ hợp lệ:");
        manager.addNewTaskWithViolations(
            "Mua sách",
            "Sách Công nghệ phần mềm.",
            "2025-07-20",
            "Cao",
            false
        );

        System.out.println("\nThêm nhiệm vụ trùng lặp (minh họa DRY - lặp lại code đọc/ghi DB và kiểm tra trùng):");
        manager.addNewTaskWithViolations(
            "Mua sách",
            "Sách Công nghệ phần mềm.",
            "2025-07-20",
            "Cao",
            false
        );

        System.out.println("\nThêm nhiệm vụ lặp lại (có chức năng xử lý):");
        JSONObject recurringTask = manager.addNewTaskWithViolations(
            "Tập thể dục",
            "Tập gym 1 tiếng.",
            "2025-07-21",
            "Trung bình",
            true
        );

        System.out.println("\nThêm nhiệm vụ với tiêu đề rỗng:");
        manager.addNewTaskWithViolations(
            "",
            "Nhiệm vụ không có tiêu đề.",
            "2025-07-22",
            "Thấp",
            false
        );

        System.out.println("\n--- Hoàn thành nhiệm vụ 'Tập thể dục' và tạo phiên bản tiếp theo ---");
        if (recurringTask != null) {
            manager.completeTaskAndGenerateNextInstance((long) recurringTask.get("id"));
        }

        System.out.println("\n--- Hoàn thành nhiệm vụ 'Mua sách' (không lặp lại) ---");
        // Giả sử ID của nhiệm vụ "Mua sách" là 1 (nếu nó được thêm thành công đầu tiên)
        manager.completeTaskAndGenerateNextInstance(1L);
    }
}
