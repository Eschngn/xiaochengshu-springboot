package com.chengliuxiang.xiaochengshu.user.biz.service;

import com.chengliuxiang.framework.common.response.Response;
import com.chengliuxiang.xiaochengshu.user.biz.model.vo.UpdateUserInfoReqVO;
import com.chengliuxiang.xiaochengshu.user.dto.req.*;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByIdRspDTO;
import com.chengliuxiang.xiaochengshu.user.dto.resp.FindUserByPhoneRspDTO;

import java.util.List;

public interface UserService {

    /**
     * 更新用户信息
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     * @param findUserByIdReqDTO
     * @return
     */
    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 根据用户 ID 集合查询用户信息
     * @param findUserByIdsReqDTO
     * @return
     */
    Response<List<FindUserByIdRspDTO>> findByIds(FindUserByIdsReqDTO findUserByIdsReqDTO);
}
