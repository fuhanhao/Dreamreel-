import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { DramaForgeJob, DramaForgeShot } from "@dreamreel/shared-types";
import {
  canGenerateShotStoryboard,
  resolveShotFailureReason,
  shotVisualStatus,
} from "./shot-status.ts";

function shot(partial: Partial<DramaForgeShot>): DramaForgeShot {
  return {
    id: "shot-1",
    episodeId: "ep-1",
    shotNumber: 1,
    description: "test",
    characterRefs: [],
    status: "pending",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...partial,
  };
}

function job(partial: Partial<DramaForgeJob>): DramaForgeJob {
  return {
    id: "job-1",
    projectId: "proj-1",
    jobType: "shot_storyboard",
    status: "failed",
    targetId: "shot-1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:01Z",
    ...partial,
  };
}

describe("shotVisualStatus storyboard", () => {
  it("shows fail when first frame exists but last-frame job failed", () => {
    const s = shot({
      firstFrameUrl: "https://example.com/first.png",
      lastFrameUrl: null,
      status: "pending",
    });
    const jobs = [
      job({
        errorMessage: "尾帧生成失败: seedream error",
        updatedAt: "2026-01-01T00:00:05Z",
      }),
    ];
    const visual = shotVisualStatus(s, "storyboard", jobs);
    assert.equal(visual.key, "fail");
    assert.equal(
      resolveShotFailureReason(s, jobs, "storyboard"),
      "尾帧生成失败: seedream error",
    );
  });

  it("shows wait for last frame when first exists and no failure", () => {
    const s = shot({
      firstFrameUrl: "https://example.com/first.png",
      lastFrameUrl: null,
    });
    assert.equal(shotVisualStatus(s, "storyboard", []).key, "wait");
    assert.equal(shotVisualStatus(s, "storyboard", []).statusLabel, "waitTailFrame");
  });

  it("hides storyboard failure after both frames exist", () => {
    const s = shot({
      firstFrameUrl: "https://example.com/first.png",
      lastFrameUrl: "https://example.com/last.png",
      status: "storyboard_done",
      errorMessage: "旧失败",
    });
    assert.equal(shotVisualStatus(s, "storyboard", []).key, "done");
    assert.equal(resolveShotFailureReason(s, [], "storyboard"), null);
  });

  it("ignores video job failures in storyboard mode", () => {
    const s = shot({ firstFrameUrl: "https://example.com/first.png" });
    const jobs = [
      job({
        jobType: "shot_video",
        errorMessage: "视频失败",
      }),
    ];
    assert.equal(shotVisualStatus(s, "storyboard", jobs).key, "wait");
    assert.equal(resolveShotFailureReason(s, jobs, "storyboard"), null);
  });

  it("shows queued when storyboard is complete but a queued job remains", () => {
    const s = shot({
      firstFrameUrl: "https://example.com/first.png",
      lastFrameUrl: "https://example.com/last.png",
      status: "storyboard_done",
    });
    const jobs = [
      job({
        status: "queued",
        jobType: "shot_storyboard",
        errorMessage: null,
      }),
    ];
    const visual = shotVisualStatus(s, "storyboard", jobs);
    assert.equal(visual.key, "run");
    assert.equal(visual.thumbLabel, "storyboardQueued");
    assert.equal(visual.statusLabel, "storyboardQueued");
  });

  it("allows first shot storyboard without a previous last frame", () => {
    const first = shot({ id: "s1", shotNumber: 1 });
    assert.equal(canGenerateShotStoryboard(first, [first]), true);
  });

  it("requires previous shot last frame before later storyboard buttons", () => {
    const first = shot({ id: "s1", shotNumber: 1, lastFrameUrl: null });
    const second = shot({ id: "s2", shotNumber: 2 });
    assert.equal(canGenerateShotStoryboard(second, [first, second]), false);

    const firstDone = shot({
      id: "s1",
      shotNumber: 1,
      lastFrameUrl: "https://example.com/last.png",
    });
    assert.equal(canGenerateShotStoryboard(second, [firstDone, second]), true);
  });

  it("shows generating when a running storyboard job remains after frames exist", () => {
    const s = shot({
      firstFrameUrl: "https://example.com/first.png",
      lastFrameUrl: "https://example.com/last.png",
      status: "storyboard_done",
    });
    const jobs = [
      job({
        status: "running",
        jobType: "shot_storyboard",
        errorMessage: null,
      }),
    ];
    const visual = shotVisualStatus(s, "storyboard", jobs);
    assert.equal(visual.key, "run");
    assert.equal(visual.thumbLabel, "storyboardGenerating");
  });
});

describe("shotVisualStatus video", () => {
  it("treats storyboard_done with videoUrl as done so regenerate stays available", () => {
    const s = shot({
      status: "storyboard_done",
      videoJobId: "gen-1",
      videoUrl: "https://example.com/out.mp4",
    });
    const visual = shotVisualStatus(s, "video", []);
    assert.equal(visual.key, "done");
    assert.equal(visual.statusLabel, "completed");
  });

  it("shows generating only when videoJobId exists without a finished url", () => {
    const s = shot({
      status: "storyboard_done",
      videoJobId: "gen-1",
      videoUrl: null,
    });
    const visual = shotVisualStatus(s, "video", []);
    assert.equal(visual.key, "run");
    assert.equal(visual.thumbLabel, "videoGenerating");
  });

  it("ignores older failed video jobs when a newer submit completed", () => {
    const s = shot({
      status: "storyboard_done",
      videoJobId: "gen-new",
      videoUrl: null,
    });
    const jobs = [
      job({
        id: "old-fail",
        jobType: "shot_video",
        status: "failed",
        errorMessage: "ModelNotOpen",
        updatedAt: "2026-01-01T00:00:01Z",
      }),
      job({
        id: "new-submit",
        jobType: "shot_video",
        status: "completed",
        errorMessage: null,
        updatedAt: "2026-01-01T00:00:10Z",
      }),
    ];
    const visual = shotVisualStatus(s, "video", jobs);
    assert.equal(visual.key, "run");
    assert.equal(visual.statusLabel, "videoGenerating");
    assert.equal(resolveShotFailureReason(s, jobs, "video"), null);
  });

  it("shows fail when the latest video job itself failed", () => {
    const s = shot({
      status: "failed",
      videoJobId: "gen-1",
      videoUrl: null,
      errorMessage: "ModelNotOpen",
    });
    const jobs = [
      job({
        jobType: "shot_video",
        status: "failed",
        errorMessage: "ModelNotOpen",
      }),
    ];
    const visual = shotVisualStatus(s, "video", jobs);
    assert.equal(visual.key, "fail");
    assert.equal(resolveShotFailureReason(s, jobs, "video"), "ModelNotOpen");
  });
});
