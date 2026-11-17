# Enhanced MergeSort — Báo cáo thực nghiệm CUTOFF

Thư mục này chứa **báo cáo thống kê và đánh giá độ ổn định** dựa trên kết quả chạy Benchmark trên máy cá nhân.

## Tệp chính

- 📄 [Báo cáo dạng Markdown](docs/Bao_cao_thong_ke_do_on_dinh.md) — đọc trực tiếp trên GitHub.
- 📝 [Báo cáo Word](docs/Bao_cao_thong_ke_do_on_dinh.docx) — dùng để chỉnh sửa/nộp.
- 📊 `docs/figures/` — biểu đồ PNG và SVG.
- 📁 `results/summary_results.csv` và `results/best_cutoff.csv` — dữ liệu thống kê gốc.

## Kết luận tóm tắt

- CUTOFF 48: hệ số tăng tốc trung bình nhân **1,835×** so với CUTOFF 0.
- CUTOFF 64: đứng đầu **9/18** nhóm (loại dữ liệu × N).
- Khoảng cách speedup giữa 48 và 64 ~**1,06%**, chưa chứng minh ý nghĩa thống kê.
- Với Reversed N=100000, **CUTOFF 16** nhanh hơn 64 trong số các mức đã thử.

> Lưu ý: đây là dữ liệu của một đợt thực nghiệm trên một máy, không phải benchmark chính thức của nhiều cấu hình phần cứng. Cần dữ liệu thô và các phiên chạy độc lập để đánh giá khoảng tin cậy.

## Gợi ý tích hợp vào repository project

Giữ phần code tự viết của bạn trong `src/` (hoặc đúng thư mục mã nguồn hiện tại), đặt báo cáo trong `docs/`, kết quả CSV trong `results/`. Đối với 54 file dữ liệu trong `data/`, cân nhắc đưa script sinh dữ liệu và seed cố định lên GitHub; với tệp dung lượng lớn có thể chỉ chia sẻ hướng dẫn tái tạo.

Để tải lên GitHub bằng Git từ thư mục repo:

```bash
git add docs/ results/ README.md
git commit -m "Add MergeSort benchmark analysis report"
git push
```

Nếu đây là repository mới và chưa cấu hình remote, hãy thiết lập repository/remote trước khi dùng `git push`.

## Hướng dẫn tái lập thí nghiệm

### Yêu cầu môi trường
- Java Development Kit: JDK 19
- Git
- Windows PowerShell hoặc Terminal tương đương
- Không cần thư viện Java bên ngoài

### 1. Clone repository

git clone https://github.com/25021858-svg/projectdsa.git
cd projectdsa

### 2. Biên dịch chương trình

Tạo thư mục out nếu chưa có:

New-Item -ItemType Directory -Force out

Biên dịch ba file Java:

javac -encoding UTF-8 -d out src/EnhanceMergesort.java src/GenerateTestData.java src/Benchmark.java

### 3. Tạo dữ liệu kiểm thử

java -cp out GenerateTestData

Chương trình tạo 54 file trong thư mục data/.

Có thể bỏ qua bước này nếu sử dụng dữ liệu đã lưu trong repository.

### 4. Chạy thí nghiệm

java -cp out Benchmark

Lưu ý: Chạy các lệnh từ thư mục gốc project.

### 5. Kiểm tra kết quả

Khi hoàn thành, chương trình thông báo:

SUCCESS: all 54 datasets validated and benchmarked.

Các kết quả được lưu trong results/:
- raw_results.csv
- summary_results.csv
- comparison.csv
- best_cutoff.csv
- overall_summary.csv
- charts/ (18 biểu đồ SVG)

### 6. Phương pháp thực nghiệm

- 6 loại dữ liệu đầu vào
- 3 kích thước: 1000, 10000, 100000
- 3 seed: 42, 2026, 123456
- 9 giá trị CUTOFF: 0, 4, 7, 10, 16, 24, 32, 48, 64
- 5 lượt warm-up và 20 lượt đo
- Đo bằng System.nanoTime()
- Kiểm tra kết quả bằng Arrays.sort()

Chương trình chỉ đo thời gian thực hiện MergeSort,
không tính thời gian đọc file và sao chép mảng.

Lưu ý: Chạy lại Benchmark sẽ ghi đè các file
kết quả trong results/. Nên thực hiện trên
một bản clone riêng.

Thời gian chạy có thể khác giữa các máy và
các phiên JVM. Mục tiêu là tái lập phương pháp,
không phải tạo ra thời gian giống tuyệt đối.
