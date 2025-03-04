package org.grade.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("grd_student")
public class Student {
    @TableId
    private String userId;
    private String studentNumber;
    private String studentClass;
}
