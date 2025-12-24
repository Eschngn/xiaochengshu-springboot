package com.chengliuxiang.xiaochengshu.user.relation.biz.service.impl;

import com.chengliuxiang.framework.biz.context.holder.LoginUserContextHolder;
import com.chengliuxiang.framework.common.exception.BizException;
import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.enums.ResponseCodeEnum;
import com.chengliuxiang.xiaochengshu.user.relation.biz.model.vo.FollowUserReqVO;
import com.chengliuxiang.xiaochengshu.user.relation.biz.rpc.UserRpcService;
import com.chengliuxiang.xiaochengshu.user.relation.biz.service.RelationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RelationServiceImpl implements RelationService {
    @Resource
    private UserRpcService userRpcService;

    /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        Long followUserId = followUserReqVO.getFollowUserId();
        Long currentUserId= LoginUserContextHolder.getUserId();
        if(Objects.equals(followUserId,currentUserId)){
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findUserById(followUserId);
        if(Objects.isNull(findUserByIdRspDTO)){
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        return Response.success();
    }
}
