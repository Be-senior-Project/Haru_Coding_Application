import React from 'react';
import {View, StyleSheet} from 'react-native';

/**
 * 비율(0~1)만큼 채워지는 원형 링. SVG 없이 반원 두 개를 잘라 회전시켜 그린다.
 *
 * 테두리 위·오른쪽만 칠한 원은 10시 반→4시 반 반원이 칠해진다. 45도 돌리면 오른쪽 반원(12시→6시).
 * 이걸 오른쪽/왼쪽 절반 영역에 각각 넣고 돌려서, 12시부터 시계 방향으로 채운다.
 */
export default function ProgressRing({
  size, thickness, progress, color, trackColor, children,
}: {
  size: number;
  thickness: number;
  progress: number;
  color: string;
  trackColor: string;
  children?: React.ReactNode;
}) {
  const deg = Math.max(0, Math.min(progress, 1)) * 360;
  const half = size / 2;
  const arc = {
    position: 'absolute' as const,
    width: size,
    height: size,
    borderRadius: half,
    borderWidth: thickness,
    borderColor: 'transparent',
    borderTopColor: color,
    borderRightColor: color,
  };
  // 칠한 반원의 끝이 φ도(12시 기준 시계 방향)에 오도록 하는 회전값
  const rotate = (phi: number) => [{rotate: `${phi - 135}deg`}];

  return (
    <View style={{width: size, height: size}}>
      <View style={[styles.track, {width: size, height: size, borderRadius: half, borderWidth: thickness, borderColor: trackColor}]} />
      {deg > 0 && (
        <>
          {/* 오른쪽 절반: 0~180도 */}
          <View style={[styles.clip, {left: half, width: half, height: size}]}>
            <View style={[arc, {left: -half, transform: rotate(Math.min(deg, 180))}]} />
          </View>
          {/* 왼쪽 절반: 180~360도. 180도 이하일 땐 경계에 실선이 비치므로 아예 그리지 않는다 */}
          {deg > 180 && (
            <View style={[styles.clip, styles.leftHalf, {width: half, height: size}]}>
              <View style={[arc, styles.leftHalf, {transform: rotate(deg)}]} />
            </View>
          )}
        </>
      )}
      <View style={styles.center}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  track: {position: 'absolute'},
  clip: {position: 'absolute', top: 0, overflow: 'hidden'},
  leftHalf: {left: 0},
  center: {position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, alignItems: 'center', justifyContent: 'center'},
});
