import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { colors, space, type } from '../theme';
import { Avatar, ListRow, Screen, Section } from '../ui';

export type ToolId = 'device' | 'kuaishou' | 'schedule' | 'clicker';

const TOOLS: {
  id: ToolId;
  title: string;
  desc: string;
  mark: string;
  tone: 'primary' | 'tertiary' | 'neutral';
  category: string;
}[] = [
  {
    id: 'clicker',
    title: '自动点击器',
    desc: '悬浮窗选多点，循环点击，可暂停/结束',
    mark: '点',
    tone: 'tertiary',
    category: '自动化',
  },
  {
    id: 'schedule',
    title: '定时提醒',
    desc: '多少分钟后 / 指定时刻 / 每天定点发系统通知',
    mark: '定',
    tone: 'primary',
    category: '效率',
  },
  {
    id: 'kuaishou',
    title: '快手极速版签到',
    desc: '无障碍自动：开 App、跳广告、去赚钱、签到',
    mark: '签',
    tone: 'tertiary',
    category: '自动化',
  },
  {
    id: 'device',
    title: '系统能力实验室',
    desc: '震动、电量、传感器、亮度等',
    mark: '系',
    tone: 'neutral',
    category: '开发 Demo',
  },
];

export function HomeScreen({ onOpen }: { onOpen: (id: ToolId) => void }) {
  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <Screen title="工具箱" subtitle="常用小工具集中管理，建议在安卓真机使用。">
        <Section title="全部工具">
          {TOOLS.map((tool, index) => (
            <ListRow
              key={tool.id}
              title={tool.title}
              subtitle={tool.desc}
              onPress={() => onOpen(tool.id)}
              last={index === TOOLS.length - 1}
              leading={<Avatar label={tool.mark} tone={tool.tone} />}
              trailing={
                <View style={styles.trailing}>
                  <Text style={styles.category}>{tool.category}</Text>
                  <Text style={styles.chevron}>›</Text>
                </View>
              }
            />
          ))}
        </Section>

        <View style={styles.footer}>
          <Text style={styles.footerText}>Material 风格 · Expo 工具箱</Text>
        </View>
      </Screen>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1, backgroundColor: colors.surface },
  content: { paddingBottom: space.xxl },
  trailing: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
  },
  category: {
    ...type.label,
    color: colors.onSurfaceVariant,
  },
  chevron: {
    fontSize: 20,
    color: colors.onSurfaceVariant,
    fontWeight: '300',
  },
  footer: {
    marginTop: space.sm,
    alignItems: 'center',
  },
  footerText: {
    ...type.label,
    color: colors.onSurfaceVariant,
  },
});
