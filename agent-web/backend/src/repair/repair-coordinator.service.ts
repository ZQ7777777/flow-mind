import { Inject, Injectable } from "@nestjs/common";
import type { BusinessRequirement, CodeReviewReport, QualityStageResult } from "@flowmind/agent-contracts";
import { DatabaseService, type GenerationRow } from "../persistence/database.service.js";
import { EventBusService } from "../workflow/event-bus.service.js";
import { PiAdapterService, type GenerationPiCallbacks } from "../pi/pi-adapter.service.js";
import { deriveGenerationSpec } from "../generation/generation-spec.js";
import { generationContract, StagingService } from "../generation/staging.service.js";
import { TargetContractService } from "../generation/target-contract.service.js";

export interface RepairAttemptResult {
  repaired: boolean;
  infrastructureFailure: boolean;
}

@Injectable()
export class RepairCoordinatorService {
  constructor(
    @Inject(DatabaseService) private readonly database: DatabaseService,
    @Inject(TargetContractService) private readonly targets: TargetContractService,
    @Inject(StagingService) private readonly staging: StagingService,
    @Inject(PiAdapterService) private readonly pi: PiAdapterService,
    @Inject(EventBusService) private readonly events: EventBusService,
  ) {}

  async attempt(
    generation: GenerationRow,
    nextRound: number,
    stages: QualityStageResult[],
    review?: CodeReviewReport,
  ): Promise<RepairAttemptResult> {
    if (!generation.pi_session_file) return { repaired: false, infrastructureFailure: true };
    const previousRound = generation.repair_round;
    const now = new Date().toISOString();
    this.database.transaction(() => {
      this.database.db.prepare(`
        UPDATE agent_code_generation SET status = 'REPAIRING', repair_round = ?,
          can_write = 0, updated_at = ? WHERE id = ? AND generation_revision = ?
      `).run(nextRound, now, generation.id, generation.generation_revision);
      this.database.db.prepare(`
        UPDATE agent_session SET state = 'CODE_REPAIRING', row_version = row_version + 1,
          updated_at = ? WHERE id = ?
      `).run(now, generation.session_id);
    });
    const contract = generationContract(generation);
    const requirement = JSON.parse(generation.requirement_snapshot_json) as BusinessRequirement;
    const spec = deriveGenerationSpec(requirement, contract);
    const target = { targetRoot: generation.target_root, contract };
    let reported = false;
    let modelError: Error | undefined;
    const callbacks: GenerationPiCallbacks = {
      requirement,
      contract,
      spec,
      onEvent: (type, data) => this.events.publish(generation.session_id, { type, data }),
      onError: (_code, message) => { modelError = new Error(message); },
      readReference: (path) => this.targets.readReference(target, path, generation.session_id),
      readStaged: (path) => this.staging.read(this.requiredRepairing(generation.id), path).content,
      listStaged: () => this.staging.list(this.requiredRepairing(generation.id)),
      writeStaged: (path, content) => this.staging.writeDuringRepair(this.requiredRepairing(generation.id), path, content),
      deleteStaged: () => { throw new Error("Repair cannot delete Manifest files."); },
      reportComplete: (files) => {
        this.staging.completeRepair(this.requiredRepairing(generation.id), files);
        reported = true;
      },
    };
    try {
      await this.pi.runRepair(
        generation.id,
        generation.staging_dir,
        generation.pi_session_file,
        buildRepairPrompt(nextRound, stages, review),
        callbacks,
      );
      if (modelError) throw modelError;
      if (!reported) throw new Error("repair session ended without report_repair_complete");
      return { repaired: true, infrastructureFailure: false };
    } catch {
      const failedAt = new Date().toISOString();
      this.database.transaction(() => {
        this.database.db.prepare(`
          UPDATE agent_code_generation SET status = 'VERIFYING', repair_round = ?,
            last_error_code = 'AGENT_REPAIR_INFRASTRUCTURE_FAILED', updated_at = ? WHERE id = ?
        `).run(previousRound, failedAt, generation.id);
        this.database.db.prepare(`
          UPDATE agent_session SET state = 'CODE_VERIFYING', row_version = row_version + 1,
            last_error_code = 'AGENT_REPAIR_INFRASTRUCTURE_FAILED', updated_at = ? WHERE id = ?
        `).run(failedAt, generation.session_id);
      });
      return { repaired: false, infrastructureFailure: true };
    }
  }

  private requiredRepairing(generationId: string): GenerationRow {
    const generation = this.database.getGeneration(generationId);
    if (!generation || generation.status !== "REPAIRING") throw new Error("generation is no longer repairable");
    return generation;
  }
}

export function buildRepairPrompt(
  round: number,
  stages: QualityStageResult[],
  review?: CodeReviewReport,
): string {
  return [
    `Repair round ${round} of 3.`,
    "Modify only existing Manifest-managed staged files. Do not add or delete files.",
    "Resolve every hard-gate diagnostic, preserve the confirmed requirement, then call report_repair_complete.",
    JSON.stringify({ stages, review }),
  ].join("\n");
}

