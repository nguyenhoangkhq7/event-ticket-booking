-- Update any existing 'USER' roles to 'CUSTOMER'
UPDATE users SET role = 'CUSTOMER' WHERE role = 'USER';

-- Drop the old constraint
ALTER TABLE users DROP CONSTRAINT chk_users_role;

-- Add the new constraint
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'));

-- Update the default value
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'CUSTOMER';
