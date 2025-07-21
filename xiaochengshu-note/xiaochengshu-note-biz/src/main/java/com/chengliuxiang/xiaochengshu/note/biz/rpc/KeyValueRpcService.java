package com.chengliuxiang.xiaochengshu.note.biz.rpc;

import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.kv.api.KeyValueFeignApi;
import com.chengliuxiang.xiaochengshu.kv.dto.req.AddNoteContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.req.DeleteNoteContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.req.FindNoteContentReqDTO;
import com.chengliuxiang.xiaochengshu.kv.dto.rsp.FindNoteContentRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KeyValueRpcService {

    @Resource
    private KeyValueFeignApi keyValueFeignApi;

    public boolean saveNoteContent(String uuid, String content) {
        AddNoteContentReqDTO addNoteContentReqDTO = AddNoteContentReqDTO.builder()
                .uuid(uuid)
                .content(content)
                .build();
        Response<?> response = keyValueFeignApi.addNoteContent(addNoteContentReqDTO);
        return Objects.nonNull(response) && response.isSuccess();
    }

    public boolean deleteNoteContent(String uuid) {
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = DeleteNoteContentReqDTO.builder()
                .uuid(uuid)
                .build();
        Response<?> response = keyValueFeignApi.deleteNoteContent(deleteNoteContentReqDTO);
        return Objects.nonNull(response) && response.isSuccess();
    }

    public String findNoteContent(String uuid) {
        FindNoteContentReqDTO findNoteContentReqDTO = FindNoteContentReqDTO.builder().uuid(uuid).build();
        Response<FindNoteContentRspDTO> response = keyValueFeignApi.findNoteContent(findNoteContentReqDTO);
        if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }

        return response.getData().getContent();
    }

}
