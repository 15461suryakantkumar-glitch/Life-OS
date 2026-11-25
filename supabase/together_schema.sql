-- FEELORA Together One Module Supabase Schema, RPC & Row-Level Security Policies
-- Privacy Guarantee: Cycle Care (cycle_logs), Journals (quick_notes), Daily Check-Ins (daily_check_ins),
-- and SURU Memories (suru_memories) are strictly local-only and NEVER stored or synced here.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  handle text unique not null,
  bio text default '',
  preferred_language text default 'English',
  theme_preference text default 'LIGHT',
  friend_code text unique not null,
  avatar_preset text default 'STAR_BUNNY',
  avatar_url text default '',
  share_xp boolean default true,
  share_streaks boolean default true,
  share_focus boolean default false,
  share_challenges boolean default true,
  level int default 1,
  xp int default 0,
  total_xp int default 0,
  daily_streak int default 0,
  focus_minutes_today int default 0,
  focus_date_epoch_day bigint default 0,
  tasks_completed_today int default 0,
  consistency_score int default 0,
  challenges_completed int default 0,
  challenges_total int default 4,
  pet_stage_name text default 'Playful Pup',
  updated_at bigint not null
);

-- Idempotent Phase 3 column migrations for existing deployments
alter table public.profiles add column if not exists total_xp int default 0;
alter table public.profiles add column if not exists focus_date_epoch_day bigint default 0;
alter table public.profiles add column if not exists challenges_total int default 4;
alter table public.profiles add column if not exists pet_stage_name text default 'Playful Pup';

alter table public.profiles enable row level security;
drop policy if exists "Profiles readable by authenticated users" on public.profiles;
create policy "Profiles readable by authenticated users" on public.profiles
  for select to authenticated using (true);
drop policy if exists "Users manage own profile" on public.profiles;
create policy "Users manage own profile" on public.profiles
  for all to authenticated using (auth.uid() = id) with check (auth.uid() = id);

create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  addressee_id uuid not null references public.profiles(id) on delete cascade,
  requester_name text not null,
  requester_handle text not null,
  status text not null check (status in ('PENDING','ACCEPTED','DECLINED','BLOCKED')),
  is_together_one boolean not null default false,
  created_at bigint not null,
  unique (requester_id, addressee_id)
);

alter table public.friendships add column if not exists is_together_one boolean not null default false;

alter table public.friendships enable row level security;
drop policy if exists "Friendship participants only" on public.friendships;
create policy "Friendship participants only" on public.friendships for all to authenticated
  using (auth.uid() = requester_id or auth.uid() = addressee_id)
  with check (auth.uid() = requester_id or auth.uid() = addressee_id);

-- Privacy-respecting friend lookup RPC by friend_code or @handle
create or replace function public.lookup_profile_by_friend_code(p_code text)
returns table (
  id uuid,
  display_name text,
  handle text,
  bio text,
  friend_code text,
  avatar_preset text,
  avatar_url text,
  share_xp boolean,
  share_streaks boolean,
  share_focus boolean,
  share_challenges boolean,
  level int,
  xp int,
  total_xp int,
  daily_streak int,
  focus_minutes_today int,
  focus_date_epoch_day bigint,
  tasks_completed_today int,
  consistency_score int,
  challenges_completed int,
  challenges_total int,
  pet_stage_name text
)
language sql
security definer
set search_path = public
as $$
  with normalized as (
    select
      trim(coalesce(p_code, '')) as raw,
      upper(trim(coalesce(p_code, ''))) as upper_code,
      case
        when trim(coalesce(p_code, '')) = '' then ''
        when left(trim(p_code), 1) = '@' then lower(trim(p_code))
        else '@' || lower(trim(p_code))
      end as norm_handle
  )
  select
    p.id,
    p.display_name,
    p.handle,
    p.bio,
    p.friend_code,
    p.avatar_preset,
    p.avatar_url,
    p.share_xp,
    p.share_streaks,
    p.share_focus,
    p.share_challenges,
    case when p.share_xp then p.level else 1 end as level,
    case when p.share_xp then p.xp else 0 end as xp,
    case when p.share_xp then greatest(p.total_xp, p.xp) else 0 end as total_xp,
    case when p.share_streaks then p.daily_streak else 0 end as daily_streak,
    case when p.share_focus then p.focus_minutes_today else 0 end as focus_minutes_today,
    case when p.share_focus then p.focus_date_epoch_day else 0 end as focus_date_epoch_day,
    case when p.share_challenges then p.tasks_completed_today else 0 end as tasks_completed_today,
    case when p.share_challenges then p.consistency_score else 0 end as consistency_score,
    case when p.share_challenges then p.challenges_completed else 0 end as challenges_completed,
    case when p.share_challenges then p.challenges_total else 4 end as challenges_total,
    p.pet_stage_name
  from public.profiles p, normalized n
  where n.raw <> ''
    and (upper(p.friend_code) = n.upper_code or lower(p.handle) = n.norm_handle)
  limit 1;
$$;

revoke all on function public.lookup_profile_by_friend_code(text) from public;
grant execute on function public.lookup_profile_by_friend_code(text) to authenticated;

-- Live Co-Study Presence table
create table if not exists public.study_presence (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  display_name text not null default '',
  is_studying boolean not null default false,
  session_title text not null default '',
  target_minutes int not null default 25,
  started_at_millis bigint not null default 0,
  ends_at_millis bigint not null default 0,
  kutta_emotion text not null default 'HAPPY',
  cheer_note text not null default '',
  updated_at bigint not null default 0
);

alter table public.study_presence enable row level security;
drop policy if exists "Presence readable by friends" on public.study_presence;
create policy "Presence readable by friends" on public.study_presence for select to authenticated
  using (
    auth.uid() = user_id
    or exists (
      select 1 from public.friendships f
      where f.status = 'ACCEPTED'
        and ((f.requester_id = auth.uid() and f.addressee_id = user_id)
          or (f.addressee_id = auth.uid() and f.requester_id = user_id))
    )
  );
drop policy if exists "Users manage own presence" on public.study_presence;
create policy "Users manage own presence" on public.study_presence for all to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.together_messages (
  id uuid primary key default gen_random_uuid(),
  client_message_id text unique not null,
  conversation_id text not null,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  content text not null default '',
  media_url text,
  delivery_status text not null default 'SENT',
  created_at bigint not null
);

alter table public.together_messages enable row level security;
drop policy if exists "Direct chat participants read" on public.together_messages;
create policy "Direct chat participants read" on public.together_messages for select to authenticated
  using (auth.uid() = sender_id or auth.uid() = recipient_id);
drop policy if exists "Direct chat sender insert" on public.together_messages;
create policy "Direct chat sender insert" on public.together_messages for insert to authenticated
  with check (auth.uid() = sender_id);
drop policy if exists "Direct chat recipient update read" on public.together_messages;
create policy "Direct chat recipient update read" on public.together_messages for update to authenticated
  using (auth.uid() = recipient_id or auth.uid() = sender_id);

create table if not exists public.shared_goals (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.profiles(id) on delete cascade,
  partner_id uuid references public.profiles(id) on delete set null,
  partner_name text,
  title text not null,
  description text default '',
  target_value int not null default 1,
  current_value int not null default 0,
  unit_label text default 'units',
  is_completed boolean default false,
  updated_at bigint not null
);

alter table public.shared_goals enable row level security;
drop policy if exists "Goal participants manage" on public.shared_goals;
create policy "Goal participants manage" on public.shared_goals for all to authenticated
  using (auth.uid() = creator_id or auth.uid() = partner_id)
  with check (auth.uid() = creator_id or auth.uid() = partner_id);

create table if not exists public.competitions (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.profiles(id) on delete cascade,
  partner_id uuid references public.profiles(id) on delete set null,
  partner_name text,
  title text not null,
  mode text not null,
  creator_score int default 0,
  partner_score int default 0,
  days_remaining int default 7,
  is_active boolean default true,
  created_at bigint not null
);

alter table public.competitions enable row level security;
drop policy if exists "Competition participants manage" on public.competitions;
create policy "Competition participants manage" on public.competitions for all to authenticated
  using (auth.uid() = creator_id or auth.uid() = partner_id)
  with check (auth.uid() = creator_id or auth.uid() = partner_id);

create table if not exists public.together_activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  author_name text not null,
  summary text not null,
  created_at bigint not null
);

alter table public.together_activities enable row level security;
drop policy if exists "Friends see activities" on public.together_activities;
create policy "Friends see activities" on public.together_activities for select to authenticated using (true);
drop policy if exists "User posts own activity" on public.together_activities;
create policy "User posts own activity" on public.together_activities for insert to authenticated with check (auth.uid() = user_id);

create table if not exists public.together_reactions (
  id uuid primary key default gen_random_uuid(),
  activity_id uuid not null references public.together_activities(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  reaction text not null,
  unique (activity_id, user_id)
);

alter table public.together_reactions enable row level security;
drop policy if exists "Authenticated reactions" on public.together_reactions;
create policy "Authenticated reactions" on public.together_reactions for all to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.together_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reported_user_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null,
  created_at bigint not null
);

alter table public.together_reports enable row level security;
drop policy if exists "Users file reports" on public.together_reports;
create policy "Users file reports" on public.together_reports for insert to authenticated with check (auth.uid() = reporter_id);

-- Realtime publication (safe idempotent registration)
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    begin
      alter publication supabase_realtime add table public.together_messages;
    exception when duplicate_object then null;
    end;
    begin
      alter publication supabase_realtime add table public.friendships;
    exception when duplicate_object then null;
    end;
    begin
      alter publication supabase_realtime add table public.study_presence;
    exception when duplicate_object then null;
    end;
    begin
      alter publication supabase_realtime add table public.shared_goals;
    exception when duplicate_object then null;
    end;
  end if;
end
$$;
