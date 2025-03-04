package org.grade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;
import org.grade.model.Comment;

public interface CommentMapper extends BaseMapper<Comment> {
    // CommentMapper.java

        @Update("UPDATE grd_comment SET user_id=#{userId}, comment_name=#{commentName}, comment_content=#{commentContent} WHERE user_id=#{userId}")
        int updateComment(Comment comment);


}
