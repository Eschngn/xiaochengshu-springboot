package com.chengliuxiang.xiaochengshu.user.relation.biz.rpc;

import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.user.api.UserFeignApi;
import com.chengliuxiang.xiaochengshu.user.dto.req.FindUserByIdReqDTO;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UserRpcService {
    @Resource
    private UserFeignApi userFeignApi;

    public FindUserByIdRspDTO findUserById(Long userId){
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);
        Response<FindUserByIdRspDTO> response = userFeignApi.findUserById(findUserByIdReqDTO);
        if(!response.isSuccess()|| Objects.isNull(response.getData())){
            return null;
        }
        return response.getData();
    }
}
