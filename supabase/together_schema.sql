-- FEELORA Together Module Supabase Schema & Row-Level Security Policies
-- Note: Private data (Cycle Care, Journals, Daily Check-In Notes, SURU Memories) is strictly local-only and NEVER stored here.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  handle text unique not null,
  bio text default '',
  preferred_language text default 'English',
  theme_preference text default 'HYBRID_LAVENDER',
  friend_code text unique not null,
  avatar_preset text default 'STAR_BUNNY',
  avatar_url text default '',
  share_xp boolean default true,
  share_streaks boolean default true,
  share_focus boolean default false,
  share_challenges boolean default true,
  level int default 1,
  xp int default 0,
  daily_streak int default 0,
  focus_minutes_today int default 0,
  tasks_completed_today int default 0,
  consistency_score int default 0,
  challenges_completed int default 0,
  updated_at bigint not null
);

alter table public.profiles enable row level security;
create policy "Profiles readable by authenticated users" on public.profiles for select to authenticated using (true);
create policy "Users manage own profile" on public.profiles for all to authenticated using (auth.uid() = id) with check (auth.uid() = id);

create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  addressee_id uuid not null references public.profiles(id) on delete cascade,
  requester_name text not null,
  requester_handle text not null,
  status text not null check (status in ('PENDING','ACCEPTED','DECLINED','BLOCKED')),
  created_at bigint not null,
  unique (requester_id, addressee_id)
);

alter table public.friendships enable row level security;
create policy "Friendship participants only" on public.friendships for all to authenticated
  using (auth.uid() = requester_id or auth.uid() = addressee_id)
  with check (auth.uid() = requester_id or auth.uid() = addressee_id);

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
create policy "Direct chat participants read" on public.together_messages for select to authenticated
  using (auth.uid() = sender_id or auth.uid() = recipient_id);
create policy "Direct chat sender insert" on public.together_messages for insert to authenticated
  with check (auth.uid() = sender_id);
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
create policy "Friends see activities" on public.together_activities for select to authenticated using (true);
create policy "User posts own activity" on public.together_activities for insert to authenticated with check (auth.uid() = user_id);

create table if not exists public.together_reactions (
  id uuid primary key default gen_random_uuid(),
  activity_id uuid not null references public.together_activities(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  reaction text not null,
  unique (activity_id, user_id)
);

alter table public.together_reactions enable row level security;
create policy "Authenticated reactions" on public.together_reactions for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.together_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reported_user_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null,
  created_at bigint not null
);

alter table public.together_reports enable row level security;
create policy "Users file reports" on public.together_reports for insert to authenticated with check (auth.uid() = reporter_id);
