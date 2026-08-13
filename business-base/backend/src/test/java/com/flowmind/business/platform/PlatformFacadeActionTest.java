package com.flowmind.business.platform;

import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.DelegateTaskRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.api.service.ReadRecordService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformFacadeActionTest {

    @Test
    void routesEveryBusinessActionToExactlyItsPlatformMethod() {
        ProcessRuntimeService runtime = mock(ProcessRuntimeService.class);
        PlatformFacade facade = new PlatformFacade(mock(TaskQueryService.class), runtime,
                mock(ProcessDefinitionService.class), mock(AttachmentService.class),
                mock(ReadRecordService.class), mock(ProcessMonitorService.class), mock(CurrentUserProvider.class));

        ApproveTaskRequest approve = new ApproveTaskRequest(); facade.execute("approve", approve); verify(runtime).approve(approve);
        SubmitTaskRequest submit = new SubmitTaskRequest(); facade.execute("submit", submit); verify(runtime).submitTask(submit);
        RejectTaskRequest reject = new RejectTaskRequest(); facade.execute("reject", reject); verify(runtime).reject(reject);
        ReturnTaskRequest returnRequest = new ReturnTaskRequest(); facade.execute("return", returnRequest); verify(runtime).returnToStarter(returnRequest);
        WithdrawTaskRequest withdraw = new WithdrawTaskRequest(); facade.execute("withdraw", withdraw); verify(runtime).withdraw(withdraw);
        DirectSendRequest direct = new DirectSendRequest(); facade.execute("direct-send", direct); verify(runtime).directSend(direct);
        TransferTaskRequest transfer = new TransferTaskRequest(); facade.execute("transfer", transfer); verify(runtime).transfer(transfer);
        DelegateTaskRequest delegate = new DelegateTaskRequest(); facade.execute("delegate", delegate); verify(runtime).delegateTask(delegate);
        AddSignRequest addSign = new AddSignRequest(); facade.execute("add-sign", addSign); verify(runtime).addSign(addSign);
        ClaimTaskRequest claim = new ClaimTaskRequest(); facade.execute("claim", claim); verify(runtime).claim(claim);
        UnclaimTaskRequest unclaim = new UnclaimTaskRequest(); facade.execute("unclaim", unclaim); verify(runtime).unclaim(unclaim);
        facade.rejectTargetNodes("task-1"); verify(runtime).getRejectTargetNodes("task-1");
    }
}
