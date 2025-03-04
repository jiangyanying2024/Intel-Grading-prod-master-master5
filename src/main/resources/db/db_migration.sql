ALTER TABLE grd_test_paper MODIFY COLUMN paper_point TEXT;

-- 插入示例数据到 grd_answer_sheet 表
INSERT INTO grd_answer_sheet (sheet_id, paper_id, student_number, image_url, create_time, status)
VALUES (1, 1888549022906097665, 'S12345', 'http://example.com/image1.jpg', NOW(), 'PENDING');

INSERT INTO grd_answer_sheet (sheet_id, paper_id, student_number, image_url, create_time, status)
VALUES (2, 1888549022906097665, 'S12346', 'http://example.com/image2.jpg', NOW(), 'COMPLETED');

INSERT INTO grd_answer_sheet (sheet_id, paper_id, student_number, image_url, create_time, status)
VALUES (3, 1888549022906097665, 'S12347', 'http://example.com/image3.jpg', NOW(), 'PENDING');
