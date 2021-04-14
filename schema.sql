CREATE TABLE auth(
    id serial PRIMARY KEY,
    api_key varchar(100) NOT NULL,
    auth_env integer NOT NULL
);

CREATE TABLE merchant(
    id serial PRIMARY KEY,
    name varchar(50) NOT NULL,
    account_number varchar(50) NOT NULL, /*PHONE NUMBER*/
    email_address varchar(50) NOT NULL,
    account_status integer NOT NULL,
    provider integer NOT NULL,
    password varchar(4) NOT NULL,
    created timestamp DEFAULT current_timestamp,
    updated timestamp DEFAULT current_timestamp
);

CREATE TABLE merchant_payment(
    id serial PRIMARY KEY,
    user_id integer NOT NULL,
    amount numeric(10, 2) NOT NULL,
    provider integer NOT NULL,
    client varchar(50) NOT NULL,
    reason varchar(50),
    day integer NOT NULL,
    month integer NOT NULL,
    hour integer NOT NULL,
    created timestamp DEFAULT current_timestamp,
    updated timestamp DEFAULT current_timestamp
);

CREATE TABLE merchant_wallet(
    id serial PRIMARY KEY,
    user_id integer NOT NULL UNIQUE,
    currency varchar(3),
    amount numeric(10, 2) NOT NULL,
    created timestamp DEFAULT current_timestamp,
    updated timestamp DEFAULT current_timestamp
);

CREATE TABLE transaction(
    id serial PRIMARY KEY,
    transaction_id varchar(50) NOT NULL,
    user_id integer NOT NULL,
    currency varchar(3),
    amount numeric(10, 2) NOT NULL,
    transaction_type integer NOT NULL,
    status integer NOT NULL,
    provider_ref_id varchar(50) NOT NULL,
    processing_fee numeric(10,2),
    reason varchar(100),
    created timestamp DEFAULT current_timestamp,
    updated timestamp DEFAULT current_timestamp
);

CREATE TABLE user_token(
    id serial PRIMARY KEY,
    user_id integer NOT NULL,
    token VARCHAR(500) NOT NULL,
    created timestamp DEFAULT current_timestamp,
    updated timestamp DEFAULT current_timestamp
);