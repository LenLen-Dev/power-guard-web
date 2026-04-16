export interface BuildingOption {
  id: string;
  name: string;
}

export const BUILDING_OPTIONS: BuildingOption[] = [
  { id: "6", name: "男25#栋" },
  { id: "74", name: "梦溪7-9-A栋" },
  { id: "42", name: "男11#楼" },
  { id: "26", name: "女01#楼" },
  { id: "30", name: "女03#楼" },
  { id: "19", name: "女11#楼" },
  { id: "70", name: "梦溪7-6栋" },
  { id: "68", name: "梦溪7-4栋" },
  { id: "15", name: "研03#楼" },
  { id: "16", name: "研04#楼" },
  { id: "71", name: "梦溪7-7栋" },
  { id: "61", name: "女13#楼" },
  { id: "8", name: "男20#楼" },
  { id: "52", name: "男16#楼" },
  { id: "69", name: "梦溪7-5栋" },
  { id: "10", name: "男19#楼" },
  { id: "37", name: "研01#楼" },
  { id: "25", name: "男05#楼" },
  { id: "66", name: "梦溪7-2栋" },
  { id: "72", name: "梦溪7-8栋" },
  { id: "4", name: "男22#栋" },
  { id: "63", name: "研05#楼" },
  { id: "36", name: "女07#楼" },
  { id: "67", name: "梦溪7-3栋" },
  { id: "77", name: "研7#楼（南楼）" },
  { id: "57", name: "女09#楼" },
  { id: "73", name: "梦溪7-9-B栋" },
  { id: "50", name: "男15#楼" },
  { id: "2", name: "男21#栋" },
  { id: "49", name: "男14#楼" },
  { id: "76", name: "研6#楼（北楼）" },
  { id: "34", name: "女05#楼" },
  { id: "32", name: "女04#楼" },
  { id: "75", name: "梦溪7-9-C栋" },
  { id: "12", name: "男18#楼" },
  { id: "44", name: "男12#楼" },
  { id: "21", name: "女14#楼" },
  { id: "14", name: "男17#楼" },
  { id: "56", name: "女06#楼" },
  { id: "60", name: "女12#楼" },
  { id: "28", name: "女02#楼" },
  { id: "65", name: "梦溪7-1栋" },
  { id: "59", name: "女10#楼" },
  { id: "46", name: "男13#楼" },
  { id: "17", name: "女08#楼" },
  { id: "38", name: "研02#楼" }
];

export function buildRoomName(buildingName: string, roomId: string) {
  const nextBuildingName = buildingName.trim();
  const nextRoomId = roomId.trim();
  if (!nextBuildingName || !nextRoomId) {
    return "";
  }
  return `${nextBuildingName}-${nextRoomId}`;
}

export function findBuildingById(id: string) {
  return BUILDING_OPTIONS.find((item) => item.id === id) ?? null;
}
