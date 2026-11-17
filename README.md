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
