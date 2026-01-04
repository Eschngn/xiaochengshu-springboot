package com.chengliuxiang.xiaochengshu.user.relation.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.user.api.UserFeignApi;
import com.chengliuxiang.xiaochengshu.user.dto.req.FindUserByIdReqDTO;
import com.chengliuxiang.xiaochengshu.user.dto.req.FindUserByIdsReqDTO;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
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

    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds){
        FindUserByIdsReqDTO findUserByIdsReqDTO = new FindUserByIdsReqDTO();
        findUserByIdsReqDTO.setIds(userIds);
        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUserByIdsReqDTO);
        if(!response.isSuccess()|| Objects.isNull(response.getData())|| CollUtil.isEmpty(response.getData())){
            return null;
        }
        return response.getData();
    }
}
