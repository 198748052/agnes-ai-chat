package store

import (
	"encoding/json"
	"errors"
	"os"
	"sync"
)

var (
	ErrUsernameExists = errors.New("username already exists")
	ErrNotFound       = errors.New("not found")
)

type User struct {
	ID           int    `json:"id"`
	Username     string `json:"username"`
	Nickname     string `json:"nickname"`
	Salt         string `json:"salt"`
	PasswordHash string `json:"password_hash"`
	AvatarURL    string `json:"avatar_url,omitempty"`
	CreatedAt    string `json:"created_at"`
}

type usersFile struct {
	NextID int              `json:"next_id"`
	Users  map[string]*User `json:"users"`
}

type UsageRecord struct {
	ID       int    `json:"id"`
	Username string `json:"username"`
	Type     string `json:"type"`
	TS       int64  `json:"ts"`
}

type usageFile struct {
	NextID  int           `json:"next_id"`
	Records []UsageRecord `json:"records"`
}

// Store 使用两个 JSON 文件持久化用户与生成用量，行为与 simple_server.py 一致。
type Store struct {
	mu        sync.Mutex
	usersPath string
	usagePath string
	usersData usersFile
	usageData usageFile
	dirty     bool
}

func New(usersPath, usagePath string) (*Store, error) {
	s := &Store{usersPath: usersPath, usagePath: usagePath}
	if err := s.load(); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Store) load() error {
	if err := s.loadUsers(); err != nil {
		return err
	}
	return s.loadUsage()
}

func (s *Store) loadUsers() error {
	data, err := os.ReadFile(s.usersPath)
	if errors.Is(err, os.ErrNotExist) {
		s.usersData = usersFile{NextID: 1, Users: map[string]*User{}}
		return nil
	}
	if err != nil {
		return err
	}
	var uf usersFile
	if err := json.Unmarshal(data, &uf); err != nil {
		return err
	}
	if uf.Users == nil {
		uf.Users = map[string]*User{}
	}
	s.usersData = uf
	return nil
}

func (s *Store) loadUsage() error {
	data, err := os.ReadFile(s.usagePath)
	if errors.Is(err, os.ErrNotExist) {
		s.usageData = usageFile{NextID: 1, Records: []UsageRecord{}}
		return nil
	}
	if err != nil {
		return err
	}
	var uf usageFile
	if err := json.Unmarshal(data, &uf); err != nil {
		return err
	}
	if uf.Records == nil {
		uf.Records = []UsageRecord{}
	}
	s.usageData = uf
	return nil
}

func (s *Store) saveUsers() error {
	data, err := json.MarshalIndent(s.usersData, "", "  ")
	if err != nil {
		return err
	}
	return writeAtomic(s.usersPath, data)
}

func (s *Store) saveUsage() error {
	data, err := json.MarshalIndent(s.usageData, "", "  ")
	if err != nil {
		return err
	}
	return writeAtomic(s.usagePath, data)
}

func writeAtomic(path string, data []byte) error {
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// CreateUser 注册用户；用户名已存在返回 ErrUsernameExists。
func (s *Store) CreateUser(username, nickname, salt, passwordHash string) (*User, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if _, exists := s.usersData.Users[username]; exists {
		return nil, ErrUsernameExists
	}
	u := &User{
		ID:           s.usersData.NextID,
		Username:     username,
		Nickname:     nickname,
		Salt:         salt,
		PasswordHash: passwordHash,
		CreatedAt:    timeNowUTC(),
	}
	s.usersData.Users[username] = u
	s.usersData.NextID++
	if err := s.saveUsers(); err != nil {
		return nil, err
	}
	return u, nil
}

func (s *Store) GetUserByUsername(username string) (*User, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.usersData.Users[username]
	if !ok {
		return nil, ErrNotFound
	}
	return u, nil
}

func (s *Store) GetUserByID(id int) (*User, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, u := range s.usersData.Users {
		if u.ID == id {
			return u, nil
		}
	}
	return nil, ErrNotFound
}

func (s *Store) UpdateUser(username string, mutate func(*User)) (*User, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.usersData.Users[username]
	if !ok {
		return nil, ErrNotFound
	}
	mutate(u)
	if err := s.saveUsers(); err != nil {
		return nil, err
	}
	return u, nil
}

// AddUsage 追加一条生成用量记录。
func (s *Store) AddUsage(username, genType string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	rec := UsageRecord{
		ID:       s.usageData.NextID,
		Username: username,
		Type:     genType,
		TS:       timeNowUnixMS(),
	}
	s.usageData.Records = append(s.usageData.Records, rec)
	s.usageData.NextID++
	return s.saveUsage()
}

// UsageTimestamps 返回某用户某类型的生成时间戳（毫秒）。
func (s *Store) UsageTimestamps(username, genType string) []int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	var ts []int64
	for _, r := range s.usageData.Records {
		if r.Username == username && r.Type == genType {
			ts = append(ts, r.TS)
		}
	}
	return ts
}
