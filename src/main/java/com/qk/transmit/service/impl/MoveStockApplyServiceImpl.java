package com.qk.transmit.service.impl;


import com.github.pagehelper.PageInfo;
import com.google.common.collect.Maps;
import com.qk.commonservice.commonutil.UserUtils;
import com.qk.commonservice.exception.ResponseCode;
import com.qk.commonservice.service.impl.CrudServiceImpl;
import com.qk.commonservice.sysentity.User;
import com.qk.commonservice.sysentity.WorkFlow;
import com.qk.transmit.dao.MoveStockApplyDao;
import com.qk.transmit.entity.MoveStockApply;
import com.qk.transmit.service.FlowableServiceClient;
import com.qk.transmit.service.MoveStockApplyService;
import com.qk.transmit.util.TransConstant;
import com.qk.transmit.util.TransUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class MoveStockApplyServiceImpl extends CrudServiceImpl<MoveStockApplyDao, MoveStockApply>
        implements MoveStockApplyService {

    @Autowired
    private FlowableServiceClient flowableServiceClient;


    /**
     * 添加
     *
     * @param moveStockApply 移库实体对象
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseCode addMoveStockApply(MoveStockApply moveStockApply) {
        moveStockApply.setStatus(TransConstant.MOVE_STOCK_APPLY_STATUS_SUBMIT);
        User user = UserUtils.getUser();
        moveStockApply.setCreateBy(user);
        moveStockApply.setCreateName(user.getOffice().getName());
        moveStockApply.setOfficeId(user.getOffice().getId());
        save(moveStockApply);

        // 启动工作流相关参数设置
        Map<String, Object> vars = Maps.newHashMap();
        String title = "编码为" + moveStockApply.getOfficeId() + "的移库信息修改申请(" + moveStockApply.getCjh() + ")";
        vars.put("title", title);

        //拼接businessId，格式：应用名称:流程定义Key:业务记录Id:组织机构Id
        StrBuilder builder = new StrBuilder();
        String businessId = builder.append(applicationName)
                .append(":").append("MoveStockChangeApply")
                .append(":").append(moveStockApply.getId())
                .append(":").append(UserUtils.getUser().getOffice().getId())
                .toString();
        WorkFlow workFlow = new WorkFlow();
        workFlow.setProcDefKey("MoveStockChangeApply");
        workFlow.setBusinessId(businessId);
        workFlow.setVars(vars);
        // 启动流程
        String procInsId = flowableServiceClient.start(UserUtils.getCurrentToken(), workFlow);
        // 更新流程实例id
        moveStockApply.setProcessInstanceId(procInsId);
        dao.updateProcInstanceId(moveStockApply);
        return ResponseCode.OK;
    }

    /**
     * 移库申请审批
     *
     * @param moveStockApply 移库实体对象
     * @return 操作结果，OK为成功 FAIL为失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseCode auditMoveStockApply(MoveStockApply moveStockApply) {
        WorkFlow workFlow = moveStockApply.getWorkFlow();
        String comment = ("1".equals(workFlow.getFlag()) ? "[同意]" : "[驳回]")
                + (workFlow.getComment() == null ? "" : workFlow.getComment());
        workFlow.setComment(comment);
        Map<String, Object> vars;
        if (moveStockApply.getWorkFlow().getVars() != null) {
            vars = moveStockApply.getWorkFlow().getVars();
        } else {
            vars = Maps.newHashMap();
        }
        vars.put("pass", workFlow.getFlag());
        workFlow.setVars(vars);
        // 驳回更新审批状态
        if ("0".equals(workFlow.getFlag())) {
            moveStockApply.setStatus(TransConstant.MOVE_STOCK_APPLY_STATUS_REJECT);
        } else {
            moveStockApply.setStatus(TransConstant.MOVE_STOCK_APPLY_STATUS_PASS);
        }
        dao.updateStatus(moveStockApply);
        // 完成任务
        flowableServiceClient.complete(UserUtils.getCurrentToken(), workFlow);
        return ResponseCode.OK;
    }

}
